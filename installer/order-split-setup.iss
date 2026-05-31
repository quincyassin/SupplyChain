; 分单发单助手 Windows 安装包（Inno Setup 6）
; 安装向导使用 Inno Setup 内置英文（Default.isl）

#define AppVersion "1.0.0"
#define AppVersionInfo "1.0.0.0"

[Setup]
AppId={{A7B3C9D1-E2F4-4A6B-8C0D-1E2F3A4B5C6D}
AppName=分单发单助手
AppVersion={#AppVersion}
AppVerName=分单发单助手 {#AppVersion}
AppPublisher=分单发单助手
DefaultDirName={autopf}\OrderSplitMerge
DefaultGroupName=分单发单助手
DisableProgramGroupPage=yes
OutputDir=..\release\installer
OutputBaseFilename=OrderSplitMerge_Setup_{#AppVersion}
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=lowest
ArchitecturesAllowed=x64
ArchitecturesInstallIn64BitMode=x64
VersionInfoVersion={#AppVersionInfo}
VersionInfoProductName=分单发单助手
VersionInfoCompany=分单发单助手

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Additional icons:"; Flags: checkedonce

[Files]
Source: "..\release\staging\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\分单发单助手"; Filename: "{app}\start.bat"; WorkingDir: "{app}"; Comment: "Launch app"
Name: "{group}\Stop"; Filename: "{app}\stop.bat"; WorkingDir: "{app}"; Comment: "Stop service"
Name: "{group}\README"; Filename: "{app}\README.txt"
Name: "{group}\Uninstall"; Filename: "{uninstallexe}"
Name: "{autodesktop}\分单发单助手"; Filename: "{app}\start.bat"; WorkingDir: "{app}"; Tasks: desktopicon; Comment: "Launch app"

[Run]
Filename: "{app}\start.bat"; Description: "Launch 分单发单助手"; Flags: nowait postinstall skipifsilent shellexec

[UninstallRun]
Filename: "{app}\stop.bat"; Flags: runhidden waituntilterminated skipifdoesntexist
