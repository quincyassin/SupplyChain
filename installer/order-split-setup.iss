; Order Split Merge Windows Installer (Inno Setup 6)
; Wizard language: English (Default.isl). Save as UTF-8 with BOM if adding Unicode text.

#define AppVersion "1.0.0"
#define AppVersionInfo "1.0.0.0"
#define MyAppName "分单宝"

[Setup]
AppId={{A7B3C9D1-E2F4-4A6B-8C0D-1E2F3A4B5C6D}
AppName={#MyAppName}
AppVersion={#AppVersion}
AppVerName={#MyAppName} {#AppVersion}
AppPublisher={#MyAppName}
DefaultDirName={autopf}\OrderSplitMerge
DefaultGroupName={#MyAppName}
DisableProgramGroupPage=yes
OutputBaseFilename=OrderSplitMerge_Setup_{#AppVersion}
SetupIconFile=..\packaging\windows\app.ico
Compression=lzma
SolidCompression=yes
PrivilegesRequired=lowest
ArchitecturesAllowed=x64
ArchitecturesInstallIn64BitMode=x64
VersionInfoVersion={#AppVersionInfo}
VersionInfoProductName={#MyAppName}
VersionInfoCompany={#MyAppName}

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Additional icons:"; Flags: checkedonce

[Files]
Source: "..\release\staging\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "..\packaging\windows\app.ico"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\start.bat"; WorkingDir: "{app}"; IconFilename: "{app}\app.ico"
Name: "{group}\Stop"; Filename: "{app}\stop.bat"; WorkingDir: "{app}"
Name: "{group}\README"; Filename: "{app}\README.txt"
Name: "{group}\Uninstall"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\start.bat"; WorkingDir: "{app}"; IconFilename: "{app}\app.ico"; Tasks: desktopicon

[Run]
Filename: "{app}\start.bat"; Description: "Launch {#MyAppName}"; Flags: nowait postinstall skipifsilent shellexec

[UninstallRun]
Filename: "{app}\stop.bat"; Flags: runhidden waituntilterminated skipifdoesntexist

[Code]
procedure CurStepChanged(CurStep: TSetupStep);
var
  ResultCode: Integer;
  StopBat: String;
begin
  if CurStep = ssInstall then
  begin
    StopBat := ExpandConstant('{app}\stop.bat');
    if FileExists(StopBat) then
    begin
      Exec(StopBat, '', ExpandConstant('{app}'), SW_HIDE, ewWaitUntilTerminated, ResultCode);
    end;
  end;
end;
