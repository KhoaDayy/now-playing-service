import React from "react";
import { useTranslation } from "react-i18next";
import {
  Dropdown,
  DropdownTrigger,
  DropdownMenu,
  DropdownItem,
} from "@heroui/dropdown";
import { Button } from "@heroui/button";
import { Globe } from "lucide-react";

import {
  SUPPORTED_LANGUAGES,
  SupportedLanguage,
  setLanguage,
} from "@/i18n";

interface LanguageSelectorProps {
  size?: "sm" | "md" | "lg";
  variant?: "flat" | "light" | "bordered";
  showLabel?: boolean;
  className?: string;
}

export const LanguageSelector: React.FC<LanguageSelectorProps> = ({
  size = "sm",
  variant = "light",
  showLabel = true,
  className = "",
}) => {
  const { i18n } = useTranslation();
  const currentLang = (i18n.language || "vi").substring(0, 2) as SupportedLanguage;

  const currentItem =
    SUPPORTED_LANGUAGES.find((item) => item.key === currentLang) ||
    SUPPORTED_LANGUAGES[0];

  const handleSelect = (key: React.Key) => {
    setLanguage(key as SupportedLanguage);
  };

  return (
    <Dropdown placement="bottom-end">
      <DropdownTrigger>
        <Button
          size={size}
          variant={variant}
          className={`min-w-0 font-medium px-2.5 h-8 gap-1.5 text-default-600 hover:text-foreground ${className}`}
          aria-label="Chọn ngôn ngữ / Select language"
        >
          <Globe size={16} className="text-default-500" />
          <span className="text-sm">{currentItem.icon}</span>
          {showLabel && (
            <span className="text-xs font-medium tracking-wide">
              {currentItem.label}
            </span>
          )}
        </Button>
      </DropdownTrigger>
      <DropdownMenu
        aria-label="Ngôn ngữ giao diện"
        selectedKeys={new Set([currentLang])}
        selectionMode="single"
        onAction={handleSelect}
      >
        {SUPPORTED_LANGUAGES.map((item) => (
          <DropdownItem
            key={item.key}
            startContent={<span className="text-base mr-1">{item.icon}</span>}
            className={currentLang === item.key ? "text-primary font-semibold" : ""}
          >
            {item.label}
          </DropdownItem>
        ))}
      </DropdownMenu>
    </Dropdown>
  );
};

export default LanguageSelector;
