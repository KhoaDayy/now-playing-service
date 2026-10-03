package com.widdit.nowplaying;

import com.widdit.nowplaying.util.SongUtil;

public class Test2AM {
    public static void main(String[] args) {
        String testTitle = "2AM Karaoke - Justa Tee ft Big Daddy - Nguyễn Trường Official";
        String[] res = SongUtil.parseCleanTitle(testTitle);
        System.out.println("Input: " + testTitle);
        System.out.println("Parsed Title: [" + res[0] + "]");
        System.out.println("Parsed Artist: [" + res[1] + "]");
    }
}
