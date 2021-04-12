package com.wyj;

import com.wyj.baidutool.AsrMain;
import com.wyj.baidutool.DemoException;
import com.wyj.baidutool.json.JSONArray;
import com.wyj.baidutool.json.JSONObject;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * mp3根据指定时间拆分为多段pcm文件工具
 * @author: wyj
 * @date: 2021/04/09
 */
public class TestMP3ToMultiSegmentPCM {
    /**
     * 音频间隔时间，单位秒
     */
    private static int distance =30;

    public static void main(String[] args) throws IOException, InterruptedException, DemoException {
//        String path="./orignvoice/aa.mp3";
        String path="./orignvoice/vv.mp3";
        Long duration = getDuration(path);
        //获取拆分时间组
        List<TimeSlice> timeSliceList= getSlice(duration);
        System.out.println(timeSliceList);
        //通过ffmpeg转换为多段pcm
        for (TimeSlice timeSlice : timeSliceList) {
            toPCM(timeSlice, path, "./outputmergevoice");
        }
        //通过百度接口进行转译，效果并不理想
        StringBuilder sb=new StringBuilder();
        for (TimeSlice timeSlice : timeSliceList) {
            String s = timeSlice.getPath();
            AsrMain asrMain = new AsrMain(s);
            String result = asrMain.run();
            System.out.println(result);
            JSONObject jsonObject=new JSONObject(result);
            String errMsg = jsonObject.getString("err_msg");
            if(errMsg.indexOf("success")!=-1){
                JSONArray result1 = jsonObject.getJSONArray("result");
                String rt = result1.toString();
                if(rt!=null&&rt.length() > 10){
                    rt=rt.substring(1,rt.length()-2);
                    sb = sb.append(String.format("[%s]", translateTime(timeSlice.getBegin()))).append(rt).append("\n");
                }
                File file = new File(s);
                if (file.isFile() && file.exists())
                {
                    file.delete();
                }
            }
            System.out.println(sb.toString());
        }
        System.out.println(sb.toString());
    }

    private static String translateTime(String time){
        int i = Integer.parseInt(time);
        int hour = i / 60 / 60;
        int b = i - hour * 60 * 60;
        int minute = b / 60;
        int second = b - minute * 60;
        String h = String.format("%2d", hour).replace(" ", "0");
        String m = String.format("%2d", minute).replace(" ", "0");
        String s = String.format("%2d", second).replace(" ", "0");
        return h+":"+m+":"+s;
    }

    private static void toPCM(TimeSlice timeSlice,String path,String outputPath) throws IOException, InterruptedException {
        //ffmpeg -y -ss 00:00:10 -t 00:01:00 -i C:\Users\Lenovo\Desktop\pw84o-xanmq.mp3
        // -acodec pcm_s16le -f s16le -ac 2 -ar 16000 C:\Users\Lenovo\Desktop\16k.pcm
        ProcessBuilder n = new ProcessBuilder();
        List<String> meta = new ArrayList<String>();
        meta.add("./ffmpeg/ffmpeg.exe");
        meta.add("-y");
        meta.add("-ss");
        meta.add(timeSlice.getBegin());
        meta.add("-to");
        meta.add(timeSlice.getEnd());
        meta.add("-i");
        meta.add(path);
        meta.add("-acodec");
        meta.add("pcm_s16le");
        meta.add("-f");
        meta.add("s16le");
        meta.add("-ac");
        meta.add("2");
        meta.add("-ar");
        meta.add("16000");
        String oPath = outputPath + String.format("/%s-%s.pcm", Integer.parseInt(timeSlice.getBegin()) * 1000, Integer.parseInt(timeSlice.getEnd()) * 1000);
        meta.add(oPath);
        n.command(meta);
        n.redirectErrorStream(true);
        Process start = n.start();
        InputStream inputStream = start.getInputStream();
        BufferedReader input = new BufferedReader(new InputStreamReader(inputStream));
        String ss=null;
        while ((ss=input.readLine())!=null) {
            System.out.println(ss);
        }
        System.out.println(meta.toString());
        start.waitFor();
        timeSlice.setPath(oPath);
    }

    /**
     * 音频文件获取文件时长
     * @param path
     * @return
     */
    public static Long getDuration(String path) throws IOException, InterruptedException {
        ProcessBuilder n = new ProcessBuilder();
        List<String> meta = new ArrayList<String>();
        meta.add("./ffmpeg/ffmpeg.exe");
        meta.add("-i");
        meta.add(path);
        n.command(meta);
        n.redirectErrorStream(true);
        Process start = n.start();
        InputStream inputStream = start.getInputStream();
        BufferedReader input = new BufferedReader(new InputStreamReader(inputStream));
        String ss=null;
        String time=null;
        String b="Duration:";
        String e=", start:";
        while ((ss=input.readLine())!=null){
            if(ss.indexOf(b)!=-1){
                time = ss.substring(ss.indexOf(b) + b.length(), ss.indexOf(e)).trim();
            }
        }
        start.waitFor();
        //转换为秒
        long translate = translate(time);
        return translate;
    }

    private static List<TimeSlice> getSlice(long timeLength) {
        List<TimeSlice> list=new ArrayList<>();
        long duration=0L;
        while (timeLength!=duration){
            TimeSlice timeSlice = new TimeSlice();
            timeSlice.setBegin(Long.toString(duration));
            if(duration+distance<=timeLength){
                duration+=distance;
            }else{
                duration=timeLength;
            }
            timeSlice.setEnd(Long.toString(duration));
            list.add(timeSlice);
        }
        return list;
    }

    /**
     * 转换为秒
     * @param time
     * @return
     * @throws IOException
     */
    private static long translate(String time) throws IOException {
        //00:00:43.95 放弃.95,转换后为00:00:43的对应值43
        if (time == null) {
            return 0;
        }
        String[] times = time.split(":");
        int hour = Integer.parseInt(times[0]) * 60 * 60;
        int minute = Integer.parseInt(times[1])*60;
        String t = times[2];
        if(t.indexOf(".")!=-1){
            t=t.substring(0,t.indexOf("."));
        }
        int second = Integer.parseInt(t);
        return hour + minute + second;
    }
}

class TimeSlice{
    private String begin;
    private String end;
    private String path;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getBegin() {
        return begin;
    }

    public void setBegin(String begin) {
        this.begin = begin;
    }

    public String getEnd() {
        return end;
    }

    public void setEnd(String end) {
        this.end = end;
    }

    @Override
    public String toString() {
        return "TimeSlice{" +
                "begin='" + begin + '\'' +
                ", end='" + end + '\'' +
                '}';
    }
}