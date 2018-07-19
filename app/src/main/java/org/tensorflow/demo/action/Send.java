package org.tensorflow.demo.action;
import android.content.Context;
import android.util.Log;
import org.tensorflow.demo.data.Monitor;
import java.io.File;
import cn.bmob.v3.datatype.BmobFile;
import cn.bmob.v3.listener.SaveListener;
import cn.bmob.v3.listener.UploadFileListener;

/**
 * Created by wcb on 2016/6/14.
 */
public class Send {
    private Monitor monitor;
    public Send(){
      monitor = new Monitor();
    }
    public void sendThings(Context context,String name){
        monitor.setName("tensorflow");
        monitor.save(context, new SaveListener() {
            @Override
            public void onSuccess() {
                Log.e("send","数据更新成功");
            }

            @Override
            public void onFailure(int i, String s) {
                Log.e("send","数据更新失败");
            }
        });
    }

    public void sendPic( final Context context,String name,String things){
        String picPath = "sdcard/tensorflowpic/"+name+".jpg";
        monitor.setName("tensorflow");
        monitor.setThings(things);
        final BmobFile bmobFile = new BmobFile(new File(picPath));
        bmobFile.uploadblock(context, new UploadFileListener() {
            @Override
            public void onSuccess() {
                monitor.setUrl(bmobFile.getFileUrl(context));
                monitor.save(context, new SaveListener() {
                    @Override
                    public void onSuccess() {
                        Log.e("send","数据更新成功");
                    }
                    @Override
                    public void onFailure(int i, String s) {
                    }
                });
                Log.e("send","url上传成功");
            }
            @Override
            public void onProgress(Integer value) {
                // 返回的上传进度（百分比）
            }
            @Override
            public void onFailure(int code, String msg) {
            }
        });
    }
}
