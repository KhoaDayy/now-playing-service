import { useLocation } from "react-router-dom";
import { useEffect } from "react";
import { useTranslation } from "react-i18next";

export function TitleUpdater() {
  const location = useLocation();
  const { t, i18n } = useTranslation();

  useEffect(() => {
    const titleMap: Record<string, string> = {
      "/": `${t("nav.home")} | Now Playing`,
      "/widgetDesktop": `Now Playing ${t("nav.widget")}`,
      "/lyric": `Now Playing ${t("nav.lyric")}`,
      "/player": `Now Playing ${t("nav.player")}`,
      "/markdown/editor": "Markdown Editor",
      "/settings": `${t("nav.general")} | Now Playing`,
      "/settings/general": `${t("nav.general")} | Now Playing`,
      "/settings/lyric": `${t("nav.lyric")} | Now Playing`,
      "/settings/player": `${t("nav.player")} | Now Playing`,
      "/settings/desktop": `${t("nav.desktop")} | Now Playing`,
      "/settings/camera": `${t("nav.camera")} | Now Playing`,
      "/settings/output": `${t("nav.output")} | Now Playing`,
      "/apiPage": `${t("nav.api")} | Now Playing`,
      "/extension": `${t("nav.extension")} | Now Playing`,
      "/extension/window": `${t("nav.windowMode")} | Now Playing`,
      "/extension/deployment": `${t("nav.deployment")} | Now Playing`,
      "/sponsor": `${t("nav.sponsor")} | Now Playing`,
      "/about": `${t("nav.about")} | Now Playing`,
    };

    let title = titleMap[location.pathname];

    if (!title && location.pathname.startsWith("/lyric/")) {
      title = `Now Playing ${t("nav.lyric")}`;
    }

    if (title) {
      document.title = title;
    } else {
      document.title = "404 | Now Playing";
    }
  }, [location.pathname, i18n.language, t]);

  return null;
}
