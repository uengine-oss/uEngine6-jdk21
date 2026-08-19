*** Settings ***
Library    UEngineLibrary

*** Tasks ***
Validate General Catalog Keywords
    Log To Console    hello
    Sleep    1s
    Open Browser    https://example.com    chromium
    Go To Url    https://example.com/page
    Click Element    \#submit    10s
    Input Text    \#name    Kim    true
    Select Option    \#country    KR
    Wait For Element    h1    10s
    Save Element Text As Output    h1    pageTitle
    Take Browser Screenshot    screenshots/browser.png
    Close Browser
    Click Screen    100    100    left
    Type Text    hello    0.02
    Press Key    enter
    Press Hotkey    ctrl+s
    Take Desktop Screenshot    screenshots/desktop.png
    Read Text File As Output    input.txt    content    utf-8
    Write Text File    output.txt    hello    utf-8
    Copy File    input.txt    copied.txt
    Move File    copied.txt    moved.txt
    List Folder As Output    .    files    *.txt
    Http Get As Output    https://example.com    getResult    {}    30
    Http Post Json As Output    https://example.com    {}    postResult    {}    30
    Set Process Output    result    success
    Json Value As Output    {"value":42}    value    jsonValue
