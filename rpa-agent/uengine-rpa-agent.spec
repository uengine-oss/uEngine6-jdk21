# -*- mode: python ; coding: utf-8 -*-
import sys

from PyInstaller.utils.hooks import collect_all

datas = [('uengine_rpa/UEngineLibrary.py', 'uengine_rpa')]
binaries = []
hiddenimports = ['uengine_rpa.UEngineLibrary']
tmp_ret = collect_all('robot')
datas += tmp_ret[0]; binaries += tmp_ret[1]; hiddenimports += tmp_ret[2]
tmp_ret = collect_all('playwright')
datas += tmp_ret[0]; binaries += tmp_ret[1]; hiddenimports += tmp_ret[2]
tmp_ret = collect_all('pyautogui')
datas += tmp_ret[0]; binaries += tmp_ret[1]; hiddenimports += tmp_ret[2]
tmp_ret = collect_all('pyperclip')
datas += tmp_ret[0]; binaries += tmp_ret[1]; hiddenimports += tmp_ret[2]

# pystray는 실행 OS에 맞는 backend를 importlib로 동적 로딩하므로 PyInstaller가
# 정적 분석만으로 찾지 못한다. 빌드 플랫폼의 backend만 명시적으로 포함한다.
if sys.platform == 'win32':
    hiddenimports += ['pystray._win32']
elif sys.platform == 'darwin':
    hiddenimports += ['pystray._darwin']
else:
    hiddenimports += ['pystray._xorg']


a = Analysis(
    ['tray_entry.py'],
    pathex=[],
    binaries=binaries,
    datas=datas,
    hiddenimports=hiddenimports,
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    noarchive=False,
    optimize=0,
)
pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.datas,
    [],
    name='uengine-rpa-agent',
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    upx_exclude=[],
    runtime_tmpdir=None,
    console=False,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
)
if sys.platform == 'darwin':
    app = BUNDLE(
        exe,
        name='uengine-rpa-agent.app',
        icon=None,
        bundle_identifier=None,
    )
