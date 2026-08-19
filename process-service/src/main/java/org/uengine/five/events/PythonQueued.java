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
