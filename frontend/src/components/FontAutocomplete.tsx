import { useTranslation } from "react-i18next";
import React from "react";
import { Autocomplete, AutocompleteItem } from "@heroui/autocomplete";
import { FontProvider, useFonts } from "@/contexts/FontContext";
import { Button } from "@heroui/button";

interface FontAutocompleteProps {
  selectedKey?: string;
  onSelectionChange?: (key: string) => void;
}

const FontAutocompleteInner: React.FC<FontAutocompleteProps> = ({
                                                                  selectedKey,
                                                                  onSelectionChange,
                                                                }) => {
  const { t } = useTranslation();
  const { fonts, isLoading, error, needsAuthorization, authorize } = useFonts();

  // 需要授权时显示授权按钮
  if (needsAuthorization) {
    return (
      <Button
        fullWidth
        size="lg"
        variant="flat"
        disabled={isLoading}
        onPress={authorize}
      >
        {isLoading ? t("font.fetching", "获取中...") : t("font.authorizeLocalFonts", "点击授权获取本地字体")}
      </Button>
    );
  }

  return (
    <Autocomplete
      className="w-full font-poppins [&_[data-slot=inner-wrapper]]:pl-1"
      classNames={{
        listboxWrapper: "overflow-y-clip"
      }}
      selectedKey={selectedKey}
      size="lg"
      maxListboxHeight={400}
      isLoading={isLoading}
      isDisabled={!!error}
      placeholder={error || undefined}
      scrollShadowProps={{
        isEnabled: false,
        hideScrollBar: false,
      }}
      listboxProps={{
        emptyContent: <span className="text-base">{t("font.noMatch", "暂无匹配结果")}</span>
      }}
      onSelectionChange={(key) => {
        if (onSelectionChange) {
          onSelectionChange(String(key));
        }
      }}
    >
      {fonts.map((font) => (
        <AutocompleteItem
          key={font.postscriptName}
          className="h-11 mb-1 last:mb-0"
          classNames={{
            base: "px-4",
            title: "text-base font-poppins",
          }}
        >
          {font.fullName}
        </AutocompleteItem>
      ))}
    </Autocomplete>
  );
};

// 导出的组件自动包含 Provider
export const FontAutocomplete: React.FC<FontAutocompleteProps> = (props) => {
  return (
    <FontProvider>
      <FontAutocompleteInner {...props} />
    </FontProvider>
  );
};
