package com.widdit.nowplaying;

import com.widdit.nowplaying.util.SongUtil;
import com.widdit.nowplaying.util.SongMatchingUtil;

public class TestKaraokeCases {
    public static void main(String[] args) {
        String[] titles = {
            "[Karaoke] Nàng Thơ Tone Nam Beat Chuẩn",
            "Nàng Thơ Karaoke - Huy Beat",
            "Hoa Nở Không Màu | Beat Chuẩn Tone Nữ",
            "Ai Chung Tình Được Mãi - Đinh Tùng Huy | Kênh Organ Nhạc Sống",
            "Có Chàng Trai Viết Lên Cây - Beat Phối Chuẩn",
            "Sau Lời Từ Khước - Phan Mạnh Quỳnh (Karaoke Beat Chuẩn)",
            "Khóa Ly Biệt Karaoke Tone Nam Beat Chuẩn",
            "Từng Quen Karaoke Beat Phối Chuẩn",
            "Ngày Chưa Giông Bão (Tone Nữ) - Bùi Lan Hương Beat Chuẩn",
            "2AM Karaoke - Justa Tee ft Big Daddy"
        };

        for (String t : titles) {
            String[] res = SongUtil.parseCleanTitle(t);
            String keyword = SongUtil.getBestSearchKeyword(t);
            System.out.println("----------------------------------------");
            System.out.println("Original: " + t);
            System.out.println("Parsed Title: [" + res[0] + "] | Parsed Artist: [" + res[1] + "]");
            System.out.println("Search Keyword: [" + keyword + "]");
        }
    }
}
