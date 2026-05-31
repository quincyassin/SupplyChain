; 分单发单助手 Windows 安装包（Inno Setup 6）
; 构建前请先运行 scripts\build-windows-release.ps1 生成 release\staging

#define AppVersion "1.0.0"

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
SetupIconFile=
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
UninstallDisplayIcon={app}\app\order-split-merge.jar
VersionInfoVersion={#AppVersion}
VersionInfoProductName=分单发单助手
VersionInfoCompany=分单发单助手

[Languages]
Name: "chinesesimplified"; MessagesFile: "compiler:Languages\ChineseSimplified.isl"

[Tasks]
Name: "desktopicon"; Description: "创建桌面快捷方式"; GroupDescription: "附加图标:"; Flags: checkedonce

[Files]
Source: "..\release\staging\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\分单发单助手"; Filename: "{app}\启动.bat"; WorkingDir: "{app}"; Comment: "启动分单发单助手"
Name: "{group}\停止分单发单助手"; Filename: "{app}\停止.bat"; WorkingDir: "{app}"; Comment: "停止后台服务"
Name: "{group}\使用说明"; Filename: "{app}\使用说明.txt"
Name: "{group}\卸载分单发单助手"; Filename: "{uninstallexe}"
Name: "{autodesktop}\分单发单助手"; Filename: "{app}\启动.bat"; WorkingDir: "{app}"; Tasks: desktopicon; Comment: "启动分单发单助手"

[Run]
Filename: "{app}\启动.bat"; Description: "立即启动分单发单助手"; Flags: nowait postinstall skipifsilent shellexec

[UninstallRun]
Filename: "{app}\停止.bat"; Flags: runhidden waituntilterminated skipifdoesntexist

[Code]
procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
begin
  if CurUninstallStep = usUninstall then
  begin
    { 卸载时确保后台 Java 进程已结束 }
  end;
end;
