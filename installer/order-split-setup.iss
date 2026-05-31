; 分单发单助手 Windows 安装包（Inno Setup 6）
; 语言包使用仓库内 installer/Languages/ChineseSimplified.isl（{src} 指向本 iss 所在目录）

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
Name: "cn"; MessagesFile: "{src}\Languages\ChineseSimplified.isl"

[Tasks]
Name: "desktopicon"; Description: "创建桌面快捷方式"; GroupDescription: "附加图标:"; Flags: checkedonce

[Files]
Source: "..\release\staging\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\分单发单助手"; Filename: "{app}\start.bat"; WorkingDir: "{app}"; Comment: "启动分单发单助手"
Name: "{group}\停止分单发单助手"; Filename: "{app}\stop.bat"; WorkingDir: "{app}"; Comment: "停止后台服务"
Name: "{group}\使用说明"; Filename: "{app}\README.txt"
Name: "{group}\卸载分单发单助手"; Filename: "{uninstallexe}"
Name: "{autodesktop}\分单发单助手"; Filename: "{app}\start.bat"; WorkingDir: "{app}"; Tasks: desktopicon; Comment: "启动分单发单助手"

[Run]
Filename: "{app}\start.bat"; Description: "立即启动分单发单助手"; Flags: nowait postinstall skipifsilent shellexec

[UninstallRun]
Filename: "{app}\stop.bat"; Flags: runhidden waituntilterminated skipifdoesntexist
