param([Parameter(Mandatory=$true)][string]$MsiPath)
$ErrorActionPreference = 'Stop'
# Deno's generated MSI is per-machine. Its WebView backend creates a cache
# beside the executable, which is not writable by ordinary Program Files users.
# Author a per-user package using Windows Installer's database API instead.
$installer = New-Object -ComObject WindowsInstaller.Installer
$database = $installer.OpenDatabase((Resolve-Path $MsiPath).Path, 1)
try {
    $statements = @(
        "DELETE FROM Property WHERE Property = 'ALLUSERS'",
        "UPDATE Property SET Value = 'sigmasd' WHERE Property = 'Manufacturer'",
        "UPDATE Property SET Value = 'Folder Camera Receiver' WHERE Property = 'ProductName'",
        "INSERT INTO Directory (Directory, Directory_Parent, DefaultDir) VALUES ('LocalAppDataFolder', 'TARGETDIR', '.')",
        "UPDATE Directory SET Directory_Parent = 'LocalAppDataFolder', DefaultDir = 'FCAMERA|Folder Camera Receiver' WHERE Directory = 'INSTALLDIR'",
        "UPDATE Shortcut SET Name = 'FCAMERA|Folder Camera Receiver' WHERE Shortcut = 'AppShortcut'"
    )
    foreach ($statement in $statements) {
        $view = $database.OpenView($statement)
        try { $view.Execute() } finally {
            $view.Close()
            [void][Runtime.InteropServices.Marshal]::ReleaseComObject($view)
        }
    }
    # The package runs without elevation (msidbSumInfoSourceTypeLUAPackage).
    $summary = $database.SummaryInformation(2)
    try {
        $flags = $summary.GetType().InvokeMember('Property', [Reflection.BindingFlags]::GetProperty, $null, $summary, @(15))
        [void]$summary.GetType().InvokeMember('Property', [Reflection.BindingFlags]::SetProperty, $null, $summary, @(15, ([int]$flags -bor 8)))
        # Native Arm64 MSIs require Windows Installer 5.0 (summary Page Count).
        [void]$summary.GetType().InvokeMember('Property', [Reflection.BindingFlags]::SetProperty, $null, $summary, @(14, 500))
        $summary.Persist()
    }
    finally { [void][Runtime.InteropServices.Marshal]::ReleaseComObject($summary) }
    $database.Commit()
} finally {
    [void][Runtime.InteropServices.Marshal]::ReleaseComObject($database)
    [void][Runtime.InteropServices.Marshal]::ReleaseComObject($installer)
}
Write-Output 'Prepared per-user Folder Camera Receiver installer.'
