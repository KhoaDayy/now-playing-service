import React, { useEffect, useState, useRef } from "react";
import { Alert } from "@heroui/alert";
import { Switch } from "@heroui/switch";
import { NumberInput } from "@heroui/number-input";
import { Button } from "@heroui/button";
import { Divider } from "@heroui/divider";
import { addToast } from "@heroui/toast";
import { useDisclosure } from "@heroui/use-disclosure";
import {
  Modal,
  ModalContent,
  ModalHeader,
  ModalBody,
  ModalFooter,
} from "@heroui/modal";
import { Spacer } from "@heroui/spacer";
import { useTranslation } from "react-i18next";
import { useOpenExternalUrl } from "@/hooks/useOpenExternalUrl";

interface OsBitResponse {
  osBit: number;
}

export default function WindowExtensionPage() {
  const { t } = useTranslation();

  // 歌曲组件状态
  const [songEnabled, setSongEnabled] = useState<boolean>();
  const [songWidth, setSongWidth] = useState<number>();
  const [songHeight, setSongHeight] = useState<number>();
  const [songWidthInvalid, setSongWidthInvalid] = useState(false);
  const [songHeightInvalid, setSongHeightInvalid] = useState(false);

  // 歌词组件状态
  const [lyricEnabled, setLyricEnabled] = useState<boolean>();
  const [lyricWidth, setLyricWidth] = useState<number>();
  const [lyricHeight, setLyricHeight] = useState<number>();
  const [lyricWidthInvalid, setLyricWidthInvalid] = useState(false);
  const [lyricHeightInvalid, setLyricHeightInvalid] = useState(false);

  // 自定义组件数据
  const [customData, setCustomData] = useState({
    enabled: false,
    width: 800,
    height: 600,
  });

  const debounceRefs = {
    songWidth: useRef<NodeJS.Timeout | null>(null),
    songHeight: useRef<NodeJS.Timeout | null>(null),
    lyricWidth: useRef<NodeJS.Timeout | null>(null),
    lyricHeight: useRef<NodeJS.Timeout | null>(null),
  };

  const {
    isOpen: is32BitModalOpen,
    onOpen: on32BitModalOpen,
    onOpenChange: on32BitModalOpenChange,
  } = useDisclosure();

  const { openExternalUrl } = useOpenExternalUrl();

  // 页面加载时获取设置
  useEffect(() => {
    fetch("/api/settings/plugin/windowWidget")
      .then((res) => {
        if (!res.ok) {
          throw new Error(`HTTP 响应错误！状态码：${res.status}`);
        }

        return res.json();
      })
      .then((data) => {
        if (data.songWindow) {
          setSongEnabled(data.songWindow.enabled ?? false);
          setSongWidth(data.songWindow.width ?? 800);
          setSongHeight(data.songWindow.height ?? 600);
        }
        if (data.lyricWindow) {
          setLyricEnabled(data.lyricWindow.enabled ?? false);
          setLyricWidth(data.lyricWindow.width ?? 800);
          setLyricHeight(data.lyricWindow.height ?? 600);
        }
        if (data.customWindow) {
          setCustomData({
            enabled: data.customWindow.enabled ?? false,
            width: data.customWindow.width ?? 800,
            height: data.customWindow.height ?? 600,
          });
        }
      })
      .catch((err) => {
        console.error("加载设置失败", err);
        addToast({
          title: t("common.loadFailed"),
          description: err.message,
          color: "danger",
          timeout: 6000,
        });
      });
  }, [t]);

  // 保存设置
  const saveSettings = (
    newSong?: { enabled?: boolean; width?: number; height?: number },
    newLyric?: { enabled?: boolean; width?: number; height?: number },
  ) => {
    const payload = {
      songWindow: {
        enabled: newSong?.enabled ?? songEnabled,
        width: newSong?.width ?? songWidth,
        height: newSong?.height ?? songHeight,
      },
      lyricWindow: {
        enabled: newLyric?.enabled ?? lyricEnabled,
        width: newLyric?.width ?? lyricWidth,
        height: newLyric?.height ?? lyricHeight,
      },
      customWindow: customData,
    };

    fetch("/api/settings/plugin/windowWidget", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    })
      .then((response) => {
        if (response.ok) {
          console.log("保存设置成功");
          addToast({
            title: t("common.saveSuccess"),
            description: t("common.saveSuccessDesc"),
            timeout: 2000,
          });
        } else {
          throw new Error(`HTTP 响应错误！状态码：${response.status}`);
        }
      })
      .catch((err) => {
        console.error("保存设置失败", err);
        addToast({
          title: t("common.saveFailed"),
          description: err.message,
          color: "danger",
          timeout: 6000,
        });
      });
  };

  return (
    <div className="flex justify-center">
      <div className="flex flex-col w-full max-w-[800px] py-6 px-10 gap-6">
        <h1 className="text-3xl text-white font-bold leading-9">{t("extension.windowMode.title")}</h1>

        <div className="flex items-center justify-center w-full">
          <Alert
            description={t("extension.windowMode.banner")}
            endContent={
              <Button
                size="sm"
                variant="flat"
                onPress={() => {
                  openExternalUrl("https://www.kdocs.cn/l/cpEGQWnJGcJL");
                }}
              >
                {t("extension.windowMode.viewTutorial")}
              </Button>
            }
            variant="faded"
          />
        </div>

        {/* 歌曲组件 */}
        <div className="flex flex-col gap-4">
          <h1 className="text-xl text-default-800 font-bold leading-9">
            {t("extension.windowMode.songWindow")}
          </h1>

          {/* 启用窗口 */}
          <div className="group relative inline-flex flex-row w-full max-w-full items-center justify-between gap-2 p-0 h-10">
            <span className="relative text-foreground select-none text-base">
              {t("common.enabled")}
            </span>
            <Switch
              isSelected={songEnabled}
              onValueChange={async (isSelected) => {
                try {
                  const response = await fetch("/api/system/osBit");

                  if (!response.ok) {
                    throw new Error(
                      `HTTP 响应错误！状态码：${response.status}`,
                    );
                  }

                  const data: OsBitResponse = await response.json();
                  const { osBit } = data;

                  if (osBit === 32) {
                    on32BitModalOpen();

                    return;
                  }
                } catch (error: any) {
                  addToast({
                    title: t("camera.getOsBitFailed"),
                    description: error.message,
                    color: "danger",
                    timeout: 6000,
                  });

                  return;
                }

                setSongEnabled(isSelected);
                saveSettings({ enabled: isSelected });
              }}
            />
          </div>

          {/* 宽度 */}
          <div className="group relative inline-flex flex-row w-full max-w-full items-center justify-between gap-2 p-0">
            <span className="relative text-foreground select-none text-base">
              {t("extension.windowMode.width")}
            </span>
            <div>
              <NumberInput
                className="w-32 font-poppins"
                endContent={
                  <div className="pointer-events-none flex items-center">
                    <span className="text-default-400 text-small">px</span>
                  </div>
                }
                errorMessage={t("extension.windowMode.invalidWidth")}
                isInvalid={songWidthInvalid}
                labelPlacement="outside-left"
                maxValue={2560}
                minValue={100}
                step={1}
                value={songWidth}
                onValueChange={(val) => {
                  const isValid = typeof val === "number" && !isNaN(val);

                  setSongWidthInvalid(!isValid);
                  setSongWidth(val as number);

                  if (debounceRefs.songWidth.current)
                    clearTimeout(debounceRefs.songWidth.current);
                  if (isValid) {
                    debounceRefs.songWidth.current = setTimeout(() => {
                      saveSettings({ width: val as number });
                    }, 1500);
                  }
                }}
              />
            </div>
          </div>

          {/* 高度 */}
          <div className="group relative inline-flex flex-row w-full max-w-full items-center justify-between gap-2 p-0">
            <span className="relative text-foreground select-none text-base">
              {t("extension.windowMode.height")}
            </span>
            <div>
              <NumberInput
                className="w-32 font-poppins"
                endContent={
                  <div className="pointer-events-none flex items-center">
                    <span className="text-default-400 text-small">px</span>
                  </div>
                }
                errorMessage={t("extension.windowMode.invalidHeight")}
                isInvalid={songHeightInvalid}
                labelPlacement="outside-left"
                maxValue={2560}
                minValue={100}
                step={1}
                value={songHeight}
                onValueChange={(val) => {
                  const isValid = typeof val === "number" && !isNaN(val);

                  setSongHeightInvalid(!isValid);
                  setSongHeight(val as number);

                  if (debounceRefs.songHeight.current)
                    clearTimeout(debounceRefs.songHeight.current);
                  if (isValid) {
                    debounceRefs.songHeight.current = setTimeout(() => {
                      saveSettings({ height: val as number });
                    }, 1500);
                  }
                }}
              />
            </div>
          </div>
        </div>

        <Divider />

        {/* 歌词组件 */}
        <div className="flex flex-col gap-4">
          <h1 className="text-xl text-default-800 font-bold leading-9">
            {t("extension.windowMode.lyricWindow")}
          </h1>

          {/* 启用窗口 */}
          <div className="group relative inline-flex flex-row w-full max-w-full items-center justify-between gap-2 p-0 h-10">
            <span className="relative text-foreground select-none text-base">
              {t("common.enabled")}
            </span>
            <Switch
              isSelected={lyricEnabled}
              onValueChange={async (isSelected) => {
                try {
                  const response = await fetch("/api/system/osBit");

                  if (!response.ok) {
                    throw new Error(
                      `HTTP 响应错误！状态码：${response.status}`,
                    );
                  }

                  const data: OsBitResponse = await response.json();
                  const { osBit } = data;

                  if (osBit === 32) {
                    on32BitModalOpen();

                    return;
                  }
                } catch (error: any) {
                  addToast({
                    title: t("camera.getOsBitFailed"),
                    description: error.message,
                    color: "danger",
                    timeout: 6000,
                  });

                  return;
                }

                setLyricEnabled(isSelected);
                saveSettings(undefined, { enabled: isSelected });
              }}
            />
          </div>

          {/* 宽度 */}
          <div className="group relative inline-flex flex-row w-full max-w-full items-center justify-between gap-2 p-0">
            <span className="relative text-foreground select-none text-base">
              {t("extension.windowMode.width")}
            </span>
            <div>
              <NumberInput
                className="w-32 font-poppins"
                endContent={
                  <div className="pointer-events-none flex items-center">
                    <span className="text-default-400 text-small">px</span>
                  </div>
                }
                errorMessage={t("extension.windowMode.invalidWidth")}
                isInvalid={lyricWidthInvalid}
                labelPlacement="outside-left"
                maxValue={2560}
                minValue={100}
                step={1}
                value={lyricWidth}
                onValueChange={(val) => {
                  const isValid = typeof val === "number" && !isNaN(val);

                  setLyricWidthInvalid(!isValid);
                  setLyricWidth(val as number);

                  if (debounceRefs.lyricWidth.current)
                    clearTimeout(debounceRefs.lyricWidth.current);
                  if (isValid) {
                    debounceRefs.lyricWidth.current = setTimeout(() => {
                      saveSettings(undefined, { width: val as number });
                    }, 1500);
                  }
                }}
              />
            </div>
          </div>

          {/* 高度 */}
          <div className="group relative inline-flex flex-row w-full max-w-full items-center justify-between gap-2 p-0">
            <span className="relative text-foreground select-none text-base">
              {t("extension.windowMode.height")}
            </span>
            <div>
              <NumberInput
                className="w-32 font-poppins"
                endContent={
                  <div className="pointer-events-none flex items-center">
                    <span className="text-default-400 text-small">px</span>
                  </div>
                }
                errorMessage={t("extension.windowMode.invalidHeight")}
                isInvalid={lyricHeightInvalid}
                labelPlacement="outside-left"
                maxValue={2560}
                minValue={100}
                step={1}
                value={lyricHeight}
                onValueChange={(val) => {
                  const isValid = typeof val === "number" && !isNaN(val);

                  setLyricHeightInvalid(!isValid);
                  setLyricHeight(val as number);

                  if (debounceRefs.lyricHeight.current)
                    clearTimeout(debounceRefs.lyricHeight.current);
                  if (isValid) {
                    debounceRefs.lyricHeight.current = setTimeout(() => {
                      saveSettings(undefined, { height: val as number });
                    }, 1500);
                  }
                }}
              />
            </div>
          </div>
        </div>

        <Spacer y={2} />

        {/* 32 位系统模态框 */}
        <Modal isOpen={is32BitModalOpen} onOpenChange={on32BitModalOpenChange}>
          <ModalContent>
            {(onClose) => (
              <>
                <ModalHeader className="flex flex-col gap-1">{t("common.notice")}</ModalHeader>
                <ModalBody>
                  <p className="leading-7">
                    {t("camera.modal32BitBody")}
                  </p>
                </ModalBody>
                <ModalFooter>
                  <Button color="primary" onPress={onClose}>
                    {t("common.confirm")}
                  </Button>
                </ModalFooter>
              </>
            )}
          </ModalContent>
        </Modal>
      </div>
    </div>
  );
}
