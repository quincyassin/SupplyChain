; Order Split Merge Windows Installer (Inno Setup 6)
; Wizard language: English (Default.isl). Save as UTF-8 with BOM if adding Unicode text.

#define AppVersion "1.0.0"
#define AppVersionInfo "1.0.0.0"

[Setup]
AppId={{A7B3C9D1-E2F4-4A6B-8C0D-1E2F3A4B5C6D}
AppName=OrderSplitMerge
AppVersion={#AppVersion}
AppVerName=OrderSplitMerge {#AppVersion}
AppPublisher=OrderSplitMerge
DefaultDirName={autopf}\OrderSplitMerge
DefaultGroupName=OrderSplitMerge
DisableProgramGroupPage=yes
OutputBaseFilename=OrderSplitMerge_Setup_{#AppVersion}
Compression=lzma
SolidCompression=yes
PrivilegesRequired=lowest
ArchitecturesAllowed=x64
ArchitecturesInstallIn64BitMode=x64
VersionInfoVersion={#AppVersionInfo}
VersionInfoProductName=OrderSplitMerge
VersionInfoCompany=OrderSplitMerge

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Additional icons:"; Flags: checkedonce

[Files]
Source: "..\release\staging\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\OrderSplitMerge"; Filename: "{app}\start.bat"; WorkingDir: "{app}"
Name: "{group}\Stop"; Filename: "{app}\stop.bat"; WorkingDir: "{app}"
Name: "{group}\README"; Filename: "{app}\README.txt"
Name: "{group}\Uninstall"; Filename: "{uninstallexe}"
Name: "{autodesktop}\OrderSplitMerge"; Filename: "{app}\start.bat"; WorkingDir: "{app}"; Tasks: desktopicon

[Run]
Filename: "{app}\start.bat"; Description: "Launch OrderSplitMerge"; Flags: nowait postinstall skipifsilent shellexec

[UninstallRun]
Filename: "{app}\stop.bat"; Flags: runhidden waituntilterminated skipifdoesntexist
