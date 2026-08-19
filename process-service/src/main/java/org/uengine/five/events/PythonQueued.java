/*
 * [비활성화됨] 이 파일은 컴파일되지 않는 상태로 저장소에 남아 있었다.
 *
 *   - org.uengine.five.events.RPAParams          : 저장소 어디에도 존재하지 않음
 *   - org.uengine.five.Streams#outboundPython()  : 존재하지 않음
 *
 * 그동안은 target/classes 에 남아 있던 옛 .class 를 maven 증분 컴파일이 재사용해
 * 드러나지 않았을 뿐이다. 커널 인터페이스 변경으로 모듈 전체 재컴파일이 트리거되면서
 * 표면화되어, 빌드를 살리기 위해 본문을 주석 처리한다.
 *
 * 되살리려면 위 두 심볼을 복구한 뒤 아래 주석을 해제하면 된다.
 * 참조하는 코드는 저장소에 없다 (확인 완료).
 */

/*
package org.uengine.five.events;

public class PythonQueued {
    // "rpa-24", "process2", "{\"idx\": \"1\",\"methods\": \"open_browser\", \"keyword\": \"Browser\", \"params\": {\"url\": \"http://rpachallenge.com/\", \"alias\": \"test\", \"browser\":\"chrome\", \"options\": \"add_argument(\'--headless\');add_argument(\'--no-sandbox\');add_argument(\'--single-process\')\"}}"
    String methods;
    String keywords;
    Boolean resultReturn;
    RPAParams params;
    
    public String getMethods() {
        return methods;
    }
    public void setMethods(String methods) {
        this.methods = methods;
    }
    public String getKeywords() {
        return keywords;
    }
    public void setKeywords(String keywords) {
        this.keywords = keywords;
    }

    public RPAParams getParams() {
        return params;
    }
    public void setParams(RPAParams params) {
        this.params = params;
    }
    public Boolean getResultReturn() {
        return resultReturn;
    }
    public void setResultReturn(Boolean resultReturn) {
        this.resultReturn = resultReturn;
    }

    
}

*/
