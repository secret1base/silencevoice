package com.wyj;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * 音频静默时间截取工具：
 * 通过ffmpeg获取音频静默时间段=>通过静默时间段截取非静默音频=>将非静默音频合并为完整音频
 * @author: wyj
 * @date: 2021/03/18
 */
public class TestSliceVoice {
    private static String endTime;
    public static void main(String[] args) throws IOException, InterruptedException {
        String orginMP3Path="./orignvoice/xx.mp3";
        sliceSilenceVoice(orginMP3Path,"./outputmergevoice/merge.mp3");
    }

    public static void sliceSilenceVoice(String orginMP3Path,String targetMP3Path){
        try {
            ProcessBuilder n = new ProcessBuilder();
            List<String> meta = new ArrayList<String>();
            meta.add("./ffmpeg/ffmpeg.exe");
            meta.add("-i");
            meta.add(orginMP3Path);
            meta.add("-af");
            meta.add("silencedetect=n=-30dB:d=0.5");
            //-30db代表将30分贝下的声音作为静默
            meta.add("-f");
            meta.add("null");
            meta.add("-");
            n.command(meta);
            n.redirectErrorStream(true);
            Process start = n.start();
            InputStream inputStream = start.getInputStream();
            //获取静默音频开始结束时间
            List<SilenceInfo> list = getSilenceInfo(inputStream);
            start.waitFor();
            //音频转换为有效时间段
            Queue<String> queue=new LinkedList<>();
            List<Slice> sliceList=new ArrayList<>();
            for (int i=0;i<list.size(); i++){
                if(i==0){
                    Slice slice = new Slice();
                    slice.setStartTime("0");
                    slice.setEndTime(list.get(i).getStartTime());
                    slice.setDuration(new BigDecimal(slice.getEndTime()).subtract(new BigDecimal(slice.getStartTime())).setScale(2, RoundingMode.HALF_UP).toString());
                    sliceList.add(slice);
                    queue.add(list.get(i).getEndTime());
                }else{
                    if(queue.size()==0){
                        queue.add(list.get(i).getEndTime());
                    }else{
                        String startTime = queue.poll();
                        Slice slice = new Slice();
                        slice.setStartTime(startTime);
                        slice.setEndTime(list.get(i).getStartTime());
                        slice.setDuration(new BigDecimal(slice.getEndTime()).subtract(new BigDecimal(slice.getStartTime())).setScale(2, RoundingMode.HALF_UP).toString());
                        sliceList.add(slice);
                        queue.add(list.get(i).getEndTime());
                    }
                }
            }
            if(queue.size()>0){
                String poll = queue.poll();
                Slice slice = new Slice();
                slice.setStartTime(poll);
                slice.setEndTime(endTime);
                slice.setDuration(new BigDecimal(slice.getEndTime()).subtract(new BigDecimal(slice.getStartTime())).setScale(2, RoundingMode.HALF_UP).toString());
                sliceList.add(slice);
            }
            List<String> sliceMP3Path = sliceMP3(sliceList, orginMP3Path, "./slicevoice");
            mergeMP3(sliceMP3Path,targetMP3Path);
            //清理生成非静默录音碎片文件
            for (String s : sliceMP3Path) {
                new File(s).delete();
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }


    private static void mergeMP3(List<String> sliceMP3Path,String mergePath) throws IOException, InterruptedException {
        //ffmpeg -i "concat:C:\Users\Lenovo\Desktop\xx\1.mp3|C:\Users\Lenovo\Desktop\xx\2.mp3" -c copy C:\Users\Lenovo\Desktop\xx\merge.mp3
        ProcessBuilder n = new ProcessBuilder();
        List<String> meta = new ArrayList<String>();
        meta.add("./ffmpeg/ffmpeg.exe");
        meta.add("-i");
        StringBuilder sb=new StringBuilder();
        meta.add("");
        sb.append("\"concat:");
        String concat=null;
        for (String s : sliceMP3Path) {
            sb.append(s).append("|");
        }
        if(sb.length() > 0){
            concat = sb.substring(0, sb.length() - 1);
            concat=concat+"\"";
        }
        meta.add(concat);
        meta.add("-c");
        meta.add("copy");
        meta.add(mergePath);
        meta.add("-y");//当目标位置存在同名文件直接进行覆盖，正式使用时建议注释
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
    }

    /**
     * 进行音頻拆分
     * @param sliceList
     */
    private static List<String> sliceMP3(List<Slice> sliceList,String originMP3Path,String targetPath) throws IOException, InterruptedException {
        List<String> sliceMP3Path=new ArrayList<>();
        //ffmpeg -ss 00:00:10 -t 00:01:00 -i input.mp3 -c copy output.mp3
        //-i 输入的音频
        //-c copy 用原来的编码并复制到新文件中
        //-ss 起始时间
        //-t 截取音频时间长度
        //-ss和-t xx        // 单位：秒
        //-ss和-t xx:xx:xx  // 时:分:秒
        int i=0;
        for (Slice slice : sliceList) {
            ProcessBuilder n = new ProcessBuilder();
            List<String> meta = new ArrayList<String>();
            meta.add("./ffmpeg/ffmpeg.exe");
            meta.add("-ss");
            meta.add(slice.getStartTime());
            meta.add("-t");
            meta.add(slice.getDuration());
            meta.add("-i");
            meta.add(originMP3Path);
            meta.add("-c");
            meta.add("copy");
            ++i;
            String smp = targetPath + "/" + i + ".mp3";
            sliceMP3Path.add(smp);
            meta.add(smp);
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
        }
        return sliceMP3Path;
    }

    private static List<SilenceInfo> getSilenceInfo(InputStream inputStream) throws IOException {
        BufferedReader input = new BufferedReader(new InputStreamReader(inputStream));
        String ss=null;
        List<SilenceInfo> list=new ArrayList<>();
        Queue<String> que=new LinkedList<>();
        while ((ss=input.readLine())!=null){
//            System.out.println(ss);
            if(ss.contains("silence_start")){
                String silenceStart = ss.substring(ss.indexOf("silence_start:") + 14).trim();
                que.add(silenceStart);
            }
            if(ss.contains("silence_end")){
                String silenceEnd = ss.substring(ss.indexOf("silence_end:") + 12,ss.lastIndexOf("|")).trim();
                String silenceStart = que.poll();
                if(silenceStart==null){
                    throw new RuntimeException("获取静默时间存在异常");
                }
                SilenceInfo silenceInfo = new SilenceInfo();
                silenceInfo.setStartTime(silenceStart);
                silenceInfo.setEndTime(silenceEnd);
                list.add(silenceInfo);
            }
            if(ss.contains("time=")){
                String time = ss.substring(ss.indexOf("time=") + 5, ss.lastIndexOf("bitrate")).trim();
                String[] arr = time.split(":");
                if(arr.length>=3){
                    int hoursSecond = Integer.parseInt(arr[0]) * 60 * 60;
                    int minutesSecond = Integer.parseInt(arr[1]) * 60;
                    endTime = new BigDecimal(arr[2]).add(new BigDecimal(hoursSecond + minutesSecond)).setScale(2, BigDecimal.ROUND_HALF_UP).toString();
                }
            }
        }
        for (SilenceInfo silenceInfo : list) {
            System.out.println(silenceInfo);
        }
        System.out.println(endTime);
        return list;
    }
}
class SilenceInfo{
    private String startTime;
    private String endTime;

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    @Override
    public String toString() {
        return "SilenceInfo{" +
                "startTime='" + startTime + '\'' +
                ", endTime='" + endTime + '\'' +
                '}';
    }
}


class Slice{
    private String startTime;
    private String endTime;
    private String duration;

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    @Override
    public String toString() {
        return "Slice{" +
                "startTime='" + startTime + '\'' +
                ", endTime='" + endTime + '\'' +
                '}';
    }
}