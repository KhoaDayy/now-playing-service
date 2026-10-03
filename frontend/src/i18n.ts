import i18n from "i18next";
import { initReactI18next } from "react-i18next";

import vi from "./locales/vi.json";
import en from "./locales/en.json";
import zh from "./locales/zh.json";

const resources = {
  vi: { translation: vi },
  en: { translation: en },
  zh: { translation: zh },
};

export const SUPPORTED_LANGUAGES = [
  { key: "vi", label: "Tiếng Việt", icon: "🇻🇳" },
  { key: "en", label: "English", icon: "🇺🇸" },
  { key: "zh", label: "简体中文", icon: "🇨🇳" },
] as const;

export type SupportedLanguage = (typeof SUPPORTED_LANGUAGES)[number]["key"];

const getInitialLanguage = (): SupportedLanguage => {
  const saved = localStorage.getItem("nowplaying_lang") as SupportedLanguage;
  if (saved && (saved === "vi" || saved === "en" || saved === "zh")) {
    return saved;
  }

  const browserLang = (navigator.language || "").toLowerCase();
  if (browserLang.startsWith("vi")) {
    return "vi";
  }
  if (browserLang.startsWith("zh")) {
    return "zh";
  }
  return "vi"; // Mặc định tiếng Việt
};

const initialLang = getInitialLanguage();

i18n.use(initReactI18next).init({
  resources,
  lng: initialLang,
  fallbackLng: "en",
  interpolation: {
    escapeValue: false,
  },
});

export const setLanguage = (lang: SupportedLanguage) => {
  i18n.changeLanguage(lang);
  localStorage.setItem("nowplaying_lang", lang);
};

export default i18n;
