/*
 * Copyright 2009 Cedric Priscal
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */

package android.serialport.sample;

import android.os.Bundle;
import android.serialport.sample.gps.GpsAnalysis;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

public class ConsoleActivity extends SerialPortActivity {

    EditText mReception;

    StringBuilder tempMsg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.console);

        //		setTitle("Loopback test");
        mReception = (EditText) findViewById(R.id.EditTextReception);
        tempMsg = new StringBuilder();

        EditText Emission = (EditText) findViewById(R.id.EditTextEmission);
        Emission.setOnEditorActionListener(new OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                int i;
                CharSequence t = v.getText();
                char[] text = new char[t.length()];
                for (i = 0; i < t.length(); i++) {
                    text[i] = t.charAt(i);
                }
                try {
                    mOutputStream.write(new String(text).getBytes());
                    mOutputStream.write('\n');
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return false;
            }
        });
    }

    @Override
    protected void onDataReceived(final byte[] buffer, final int size) {
        runOnUiThread(new Runnable() {
            public void run() {
                if (mReception != null) {
                    String data = new String(buffer, 0, size);
                    mReception.append(data);

                    tempMsg.append(data);

                    int endSign = tempMsg.indexOf("\r\n");
                    if (endSign>0) {//判断是否有换行回车结束符
                        String s1 = tempMsg.substring(0,endSign);
//                        Log.i("tempwrap",s1);
//                        writeData(s1+"\n");
                        GpsAnalysis.getInstance().processNmeaData(s1);
                        //解析数据，判断是否可用，可用再上传，如何异步？
                        String s2 = tempMsg.substring(endSign+2,tempMsg.length());
                        tempMsg.delete(0,tempMsg.length());//清空tempMsg
                        tempMsg.append(s2);//新的段落
                    }
                }
            }
        });
    }
    //将内容写入txt文件
    private void writeData(String data){
        try {
            String sdCardDir = getApplicationContext().getFilesDir().getAbsolutePath();
            File saveFile = new File(sdCardDir, "data.txt");
            Writer outTxt = new OutputStreamWriter(new FileOutputStream(saveFile,true), "UTF-8");
            outTxt.write(data);
            outTxt.close();
        }catch (Exception e){
            Log.d("tag",e.toString());
        }
    }
}
