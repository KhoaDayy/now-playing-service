import i18n from "@/i18n";

export const RAW_PLATFORM_MAP: Record<string, string> = {
  netease: "网易云音乐",
  qq: "QQ音乐",
  kugou: "酷狗音乐",
  kuwo: "酷我音乐",
  spotify: "Spotify",
  ayna: "卡西米尔唱片机",
  apple: "Apple Music",
  potplayer: "PotPlayer",
  foobar: "Foobar2000",
  lx: "洛雪音乐",
  soda: "汽水音乐",
  huahua: "花花直播助手",
  musicfree: "MusicFree",
  bq: "BQ点歌姬",
  aimp: "AIMP",
  youtube: "YouTube Music",
  miebo: "咩播",
  yesplay: "YesPlayMusic",
  cider: "Cider",
  wesing: "全民K歌",
  browser: "浏览器",
  salt: "Salt Player"
};

export const getPlatformName = (platform: string): string => {
  if (!platform) return "";
  const key = `platform.${platform}`;
  if (i18n.exists(key)) {
    return i18n.t(key);
  }
  return RAW_PLATFORM_MAP[platform] ?? platform;
};

export const PLATFORM_MAP: Record<string, string> = new Proxy(RAW_PLATFORM_MAP, {
  get(target, prop: string) {
    if (typeof prop === "string") {
      const key = `platform.${prop}`;
      if (i18n.exists(key)) {
        return i18n.t(key);
      }
      return target[prop] ?? prop;
    }
    return (target as any)[prop];
  }
});
