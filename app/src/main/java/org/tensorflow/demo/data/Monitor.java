package org.tensorflow.demo.data;

/**
 * Created by wcb on 2016/6/14.
 */

import cn.bmob.v3.BmobObject;

public class Monitor extends BmobObject {
    private String name;
    private String things;
    private String url;
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getThings(){
        return things;
    }
    public void setThings(String things){
        this.things = things;
    }
    public String getUrl(){return url;}
    public void setUrl(String url){
        this.url = url;
    }
}
