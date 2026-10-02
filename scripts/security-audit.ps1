param([switch]$Offline)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$reportDirectory = Join-Path $projectRoot 'app/build/reports/security'
[IO.Directory]::CreateDirectory($reportDirectory) | Out-Null
$checks = [Collections.Generic.List[object]]::new()
function Add-Check([string]$Name, [bool]$Passed, [string]$Detail) {
    $checks.Add([pscustomobject]@{ name = $Name; passed = $Passed; detail = $Detail })
}
$androidNs = 'http://schemas.android.com/apk/res/android'
[xml]$manifest = Get-Content (Join-Path $projectRoot 'app/src/main/AndroidManifest.xml') -Raw
$app = $manifest.manifest.application
Add-Check 'Backups disabled' ($app.GetAttribute('allowBackup', $androidNs) -eq 'false') 'Application manifest'
Add-Check 'Legacy backups disabled' ($app.GetAttribute('fullBackupContent', $androidNs) -eq 'false') 'Application manifest'
Add-Check 'Cleartext disabled' ($app.GetAttribute('usesCleartextTraffic', $androidNs) -eq 'false') 'Application manifest'
$permissions = @($manifest.manifest.'uses-permission' | ForEach-Object { $_.GetAttribute('name', $androidNs) })
Add-Check 'No Internet permission' (-not ($permissions -contains 'android.permission.INTERNET')) 'Offline app does not need network sockets'
$exported = @($app.ChildNodes | Where-Object { $_ -is [Xml.XmlElement] -and $_.GetAttribute('exported', $androidNs) -eq 'true' })
Add-Check 'Only launcher exported in source' ($exported.Count -eq 1 -and $exported[0].GetAttribute('name', $androidNs) -eq '.MainActivity') 'Merged components also require device/release review'
[xml]$backup = Get-Content (Join-Path $projectRoot 'app/src/main/res/xml/data_extraction_rules.xml') -Raw
foreach ($mode in @('cloud-backup', 'device-transfer')) {
    $domains = @($backup.'data-extraction-rules'.$mode.exclude | Where-Object { $_.path -eq '.' } | ForEach-Object { $_.domain })
    $missing = @(@('root', 'file', 'database', 'sharedpref', 'external') | Where-Object { $_ -notin $domains })
    Add-Check "$mode exclusions" ($missing.Count -eq 0) 'All current credential-protected storage domains excluded'
}
$wrapper = Get-Content (Join-Path $projectRoot 'gradle/wrapper/gradle-wrapper.properties') -Raw
Add-Check 'Gradle distribution checksum pinned' ($wrapper -match '(?m)^distributionSha256Sum=[0-9a-f]{64}\r?$') 'SHA-256 pin required'
$wrapperHash = (Get-FileHash (Join-Path $projectRoot 'gradle/wrapper/gradle-wrapper.jar') -Algorithm SHA256).Hash.ToLowerInvariant()
Add-Check 'Wrapper JAR integrity' ($wrapperHash -eq '497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7') 'Verified against the official Gradle 9.6.0 checksum'
$source = @(Get-ChildItem (Join-Path $projectRoot 'app/src/main') -Recurse -File | Where-Object Extension -in '.kt', '.xml')
$unsafe = @($source | Select-String -Pattern 'android\.util\.Log|Log\.(d|e|i|v|w)\(|println\(|WebView|Runtime\.getRuntime|allowMainThreadQueries|fallbackToDestructiveMigration')
Add-Check 'No sensitive logging or unsafe runtime sinks' ($unsafe.Count -eq 0) ('Flagged source locations: ' + (($unsafe | ForEach-Object { "$($_.Path):$($_.LineNumber)" }) -join ', '))
$files = @(& git -C $projectRoot ls-files --cached --others --exclude-standard)
if ($LASTEXITCODE -ne 0) { throw 'Git inventory failed.' }
$secretPatterns = @(
    '-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----',
    'AKIA[0-9A-Z]{16}',
    'AIza[0-9A-Za-z_-]{35}',
    '(?:ghp_|github_pat_)[A-Za-z0-9_]{30,}',
    'xox[baprs]-[A-Za-z0-9-]{20,}',
    '(?i)(?:api[_-]?key|password|secret|access[_-]?token)\s*[:=]\s*["''][A-Za-z0-9_+/\-=]{16,}["'']'
)
$secretLocations = [Collections.Generic.List[string]]::new()
foreach ($relative in $files) {
    if ($relative -match '(^|/)(build|\.gradle)/|\.log$') { continue }
    $path = Join-Path $projectRoot $relative
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { continue }
    if ([IO.Path]::GetExtension($path) -notin '.kt','.kts','.properties','.toml','.xml','.md','.json','.ps1','.yml','.yaml','.gradle','.txt','.env','.pem','.key') { continue }
    $matchesFound = @(Select-String -LiteralPath $path -Pattern $secretPatterns)
    foreach ($found in $matchesFound) { $secretLocations.Add($relative + ':' + $found.LineNumber) }
}
Add-Check 'Credential pattern scan' ($secretLocations.Count -eq 0) ('Locations only; values never printed: ' + ($secretLocations -join ', '))
$releaseManifest = Join-Path $projectRoot 'app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml'
if (Test-Path -LiteralPath $releaseManifest) {
    [xml]$release = Get-Content -LiteralPath $releaseManifest -Raw
    $releaseApp = $release.manifest.application
    Add-Check 'Release is not debuggable' ($releaseApp.GetAttribute('debuggable', $androidNs) -ne 'true') 'Merged release manifest'
    $unsafeExports = @($releaseApp.ChildNodes | Where-Object {
        $_ -is [Xml.XmlElement] -and $_.GetAttribute('exported', $androidNs) -eq 'true' -and
        $_.GetAttribute('name', $androidNs) -ne 'com.resqlink.MainActivity' -and
        $_.GetAttribute('permission', $androidNs) -ne 'android.permission.DUMP'
    })
    Add-Check 'Release exported component protection' ($unsafeExports.Count -eq 0) 'Launcher and signature-protected AndroidX profile installer only'
} else {
    Add-Check 'Merged release manifest available' $false 'Build assembleRelease before auditing'
}
$inventory = Join-Path $reportDirectory 'dependencies.tsv'
$vulnerabilities = [Collections.Generic.List[object]]::new()
$dependencyCount = 0
$nativeComponentCount = 0
$osvStatus = 'not run (offline)'
try {
if (-not $Offline) {
    if (-not (Test-Path -LiteralPath $inventory)) { throw 'Run .\gradlew.bat securityDependencyInventory first.' }
    $dependencies = @(Get-Content $inventory | Where-Object { $_ -and $_ -notmatch '\tunspecified$' } | ForEach-Object {
        $parts = $_ -split '\t'
        if ($parts.Count -ne 2) { throw 'Malformed dependency inventory.' }
        [pscustomobject]@{ name = $parts[0]; version = $parts[1] }
    })
    $dependencyCount = $dependencies.Count
    for ($offset = 0; $offset -lt $dependencies.Count; $offset += 100) {
        $batch = @($dependencies | Select-Object -Skip $offset -First 100)
        $queries = @($batch | ForEach-Object { @{ package = @{ ecosystem = 'Maven'; name = $_.name }; version = $_.version } })
        $response = Invoke-RestMethod -Method Post -Uri 'https://api.osv.dev/v1/querybatch' -ContentType 'application/json' -Body (@{ queries = $queries } | ConvertTo-Json -Depth 8 -Compress) -TimeoutSec 60
        if (@($response.results).Count -ne $batch.Count) { throw 'Incomplete OSV response; audit cannot pass.' }
        for ($index = 0; $index -lt $batch.Count; $index++) {
            $result = $response.results[$index]
            do {
                foreach ($vulnerability in $result.vulns) {
                    $vulnerabilities.Add([pscustomobject]@{ package = $batch[$index].name; version = $batch[$index].version; id = $vulnerability.id })
                }
                $pageToken = $result.next_page_token
                if ($pageToken) {
                    $query = @{ package = @{ ecosystem = 'Maven'; name = $batch[$index].name }; version = $batch[$index].version; page_token = $pageToken }
                    $next = Invoke-RestMethod -Method Post -Uri 'https://api.osv.dev/v1/querybatch' -ContentType 'application/json' -Body (@{ queries = @($query) } | ConvertTo-Json -Depth 8 -Compress) -TimeoutSec 60
                    if (@($next.results).Count -ne 1) { throw 'Incomplete paginated OSV response.' }
                    $result = $next.results[0]
                }
            } while ($pageToken)
        }
    }
    Add-Check 'OSV dependency vulnerability scan' ($vulnerabilities.Count -eq 0) "$dependencyCount resolved Maven versions checked; $($vulnerabilities.Count) advisory matches"
    $catalog = Get-Content (Join-Path $projectRoot 'gradle/libs.versions.toml') -Raw
    $versionMatch = [regex]::Match($catalog, '(?m)^sqlcipher\s*=\s*"([0-9]+\.[0-9]+\.[0-9]+)"')
    if (-not $versionMatch.Success) { throw 'SQLCipher release version is unavailable.' }
    $cipherVersion = $versionMatch.Groups[1].Value
    $headers = @{ 'User-Agent' = 'ResQLink-security-audit' }
    $tree = Invoke-RestMethod -Uri ("https://api.github.com/repos/sqlcipher/sqlcipher-android/git/trees/v" + $cipherVersion + '?recursive=1') -Headers $headers -TimeoutSec 60
    if ($tree.truncated) { throw 'Incomplete SQLCipher source tree.' }
    $modules = @($tree.tree | Where-Object mode -eq '160000' | Select-Object path,sha)
    if ($modules.Count -ne 2) { throw 'Review changed SQLCipher native components before auditing.' }
    $modules | ConvertTo-Json | Set-Content (Join-Path $reportDirectory 'native-components.json') -Encoding utf8
    $nativeResults = [Collections.Generic.List[object]]::new()
    $nativeMatches = 0
    foreach ($module in $modules) {
        $native = Invoke-RestMethod -Method Post -Uri 'https://api.osv.dev/v1/query' -ContentType 'application/json' -Body (@{commit=$module.sha} | ConvertTo-Json -Compress) -TimeoutSec 60
        $nativeResults.Add([pscustomobject]@{component=$module.path; commit=$module.sha; response=$native})
        foreach ($vulnerability in $native.vulns) {
            $nativeMatches++
            $vulnerabilities.Add([pscustomobject]@{package=('native:' + $module.path); version=$module.sha; id=$vulnerability.id})
        }
    }
    $nativeComponentCount = $modules.Count
    $nativeResults.ToArray() | ConvertTo-Json -Depth 25 | Set-Content (Join-Path $reportDirectory 'native-osv.json') -Encoding utf8
    Add-Check 'OSV native commit scan' ($nativeMatches -eq 0) "$nativeComponentCount release submodule commits checked; $nativeMatches matches; vendored native coverage is limited"
    $osvStatus = 'completed'
}
 } catch {
    $osvStatus = 'failed or incomplete'
    Add-Check 'OSV audit completed' $false 'Dependency inventory or online query failed. Rerun with network access.'
}
$report = [ordered]@{
    checkedAtUtc = [DateTime]::UtcNow.ToString('o')
    checks = @($checks.ToArray())
    osvStatus = $osvStatus
    dependencyCount = $dependencyCount
    nativeComponentCount = $nativeComponentCount
    vulnerabilities = @($vulnerabilities.ToArray())
    limitations = @('Pattern scan is not exhaustive secret detection.', 'OSV coverage depends on published Maven advisories and indexed native commits; vendored native code may not be covered.', 'Does not test carrier delivery or OEM behavior; application encryption is tested separately by connectedDebugAndroidTest.')
}
$report | ConvertTo-Json -Depth 10 | Set-Content (Join-Path $reportDirectory 'audit.json') -Encoding utf8
$checks | Format-Table -AutoSize
if (@($checks | Where-Object { -not $_.passed }).Count -gt 0) { throw 'Security checks failed. See app/build/reports/security/audit.json.' }
