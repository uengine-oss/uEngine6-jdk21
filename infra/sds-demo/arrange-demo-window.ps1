param([string]$ProfilePath,[int]$Left,[int]$Top,[int]$Width,[int]$Height)
$ErrorActionPreference='Stop'
Add-Type @'
using System;
using System.Runtime.InteropServices;
public class DemoWindowLayout {
  [StructLayout(LayoutKind.Sequential)] public struct Rect { public int Left,Top,Right,Bottom; }
  public delegate bool Callback(IntPtr hwnd,IntPtr data);
  [DllImport("user32.dll")] public static extern bool EnumWindows(Callback callback,IntPtr data);
  [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr hwnd,out uint pid);
  [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr hwnd);
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hwnd,out Rect rect);
  [DllImport("dwmapi.dll")] public static extern int DwmGetWindowAttribute(IntPtr hwnd,int attribute,out Rect rect,int size);
  [DllImport("user32.dll")] public static extern IntPtr SetThreadDpiAwarenessContext(IntPtr context);
  [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hwnd,int command);
  [DllImport("user32.dll",SetLastError=true)] public static extern bool SetWindowPos(IntPtr hwnd,IntPtr after,int x,int y,int width,int height,uint flags);
}
'@
[void][DemoWindowLayout]::SetThreadDpiAwarenessContext([IntPtr](-4))
$matchingProcesses=@(Get-CimInstance Win32_Process -Filter "Name='msedge.exe'" | Where-Object { $_.CommandLine -and $_.CommandLine.Contains("--user-data-dir=$ProfilePath") -and $_.CommandLine -notmatch '--type=' })
if($matchingProcesses.Count -ne 1){ throw "Expected one demo browser process, found $($matchingProcesses.Count)" }
$targetProcessId=[uint32]$matchingProcesses[0].ProcessId
$script:demoHandle=[IntPtr]::Zero
[void][DemoWindowLayout]::EnumWindows({param($handle,$data)
  $windowProcessId=[uint32]0
  [void][DemoWindowLayout]::GetWindowThreadProcessId($handle,[ref]$windowProcessId)
  if($windowProcessId -eq $targetProcessId -and [DemoWindowLayout]::IsWindowVisible($handle)) { $script:demoHandle=$handle }
  return $true
},[IntPtr]::Zero)
if($script:demoHandle -eq [IntPtr]::Zero){throw 'Demo browser window not found'}
[void][DemoWindowLayout]::ShowWindow($script:demoHandle,9)
$outer=New-Object DemoWindowLayout+Rect
$visible=New-Object DemoWindowLayout+Rect
[void][DemoWindowLayout]::GetWindowRect($script:demoHandle,[ref]$outer)
if([DemoWindowLayout]::DwmGetWindowAttribute($script:demoHandle,9,[ref]$visible,16) -ne 0){throw 'Cannot read visible window frame'}
$x=$Left-($visible.Left-$outer.Left)
$y=$Top-($visible.Top-$outer.Top)
$w=$Width+($visible.Left-$outer.Left)+($outer.Right-$visible.Right)
$h=$Height+($visible.Top-$outer.Top)+($outer.Bottom-$visible.Bottom)
if(-not [DemoWindowLayout]::SetWindowPos($script:demoHandle,[IntPtr]::Zero,$x,$y,$w,$h,0x14)){throw 'Window placement failed'}
[void][DemoWindowLayout]::DwmGetWindowAttribute($script:demoHandle,9,[ref]$visible,16)
if($visible.Left -ne $Left -or $visible.Top -ne $Top -or ($visible.Right-$visible.Left) -ne $Width -or ($visible.Bottom-$visible.Top) -ne $Height){throw "Visible bounds mismatch: $($visible.Left),$($visible.Top),$($visible.Right-$visible.Left),$($visible.Bottom-$visible.Top); wanted $Left,$Top,$Width,$Height"}
@{left=$visible.Left;top=$visible.Top;width=$visible.Right-$visible.Left;height=$visible.Bottom-$visible.Top}|ConvertTo-Json -Compress
