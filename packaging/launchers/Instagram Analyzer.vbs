' Instagram Analyzer - Windows no-console launcher.
' Double-click this file: javaw.exe runs HIDDEN (no black console window),
' the browser opens automatically and a system-tray icon appears.
' To quit: tray icon -> "종료". (Use start.bat instead if you need to see errors.)
Option Explicit
Dim sh, fso, base, javaw, jar, dataDir, q, cmd
Set sh  = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")

base = fso.GetParentFolderName(WScript.ScriptFullName)
sh.CurrentDirectory = base

javaw = base & "\runtime\bin\javaw.exe"
If Not fso.FileExists(javaw) Then javaw = "javaw"   ' 동봉 런타임 없으면 시스템 javaw

jar     = base & "\instagram-analyze.jar"
dataDir = base & "\data"
q = Chr(34)   ' double-quote

cmd = q & javaw & q & _
      " -Dspring.profiles.active=desktop" & _
      " -Djava.awt.headless=false" & _
      " -Dinstagram.data.root=" & q & dataDir & q & _
      " -Dfile.encoding=UTF-8" & _
      " -jar " & q & jar & q

' 두 번째 인자 0 = 창 숨김, 세 번째 False = 종료 대기 안 함
sh.Run cmd, 0, False
