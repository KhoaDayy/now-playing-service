import React, { useState, useEffect, useMemo } from "react";
import { useOutletContext } from "react-router-dom";
import { Divider } from "@heroui/divider";
import { Spacer } from "@heroui/spacer";
import { useOpenExternalUrl } from "@/hooks/useOpenExternalUrl";
import { Alert } from "@heroui/alert";
import { Button } from "@heroui/button";
import {
  Modal,
  ModalContent,
  ModalHeader,
  ModalBody,
  ModalFooter,
} from "@heroui/modal";
import { useDisclosure } from "@heroui/use-disclosure";
import { addToast } from "@heroui/toast";
import { Drawer, DrawerBody, DrawerContent, DrawerFooter, DrawerHeader } from "@heroui/drawer";
import { Code } from "@heroui/code";
import { Input } from "@heroui/input";
import { Play } from "lucide-react";
import CopyButton from "@/components/CopyButton";
import SimulatedBrowserWindow from "@/components/SimulatedBrowserWindow";
import { useTranslation } from "react-i18next";

type LayoutOutletContext = {
  scrollToBottom: (behavior?: ScrollBehavior) => void;
};

export default function PageDeploymentExtensionPage() {
  const { t } = useTranslation();
  const { openExternalUrl } = useOpenExternalUrl();

  const outletContext = useOutletContext<LayoutOutletContext | undefined>();
  const scrollToBottom = outletContext?.scrollToBottom;

  const [publicPath, setPublicPath] = useState("");
  const [, setIsSendHovered] = useState<boolean>(false);
  const [url, setUrl] = useState("http://localhost:9863/public/example/index.html");
  const [iframeSrc, setIframeSrc] = useState("http://localhost:9863/public/example/index.html");
  const [iframeKey, setIframeKey] = useState(0);

  const {
    isOpen: isDrawerOpen,
    onOpen: onDrawerOpen,
    onOpenChange: onDrawerOpenChange,
  } = useDisclosure();

  const {
    isOpen: isRestoreExampleModalOpen,
    onOpen: onRestoreExampleModalOpen,
    onOpenChange: onRestoreExampleModalOpenChange,
  } = useDisclosure();

  // 获取软件公共目录
  useEffect(() => {
    fetch("/api/system/installPath")
      .then((res) => {
        if (!res.ok) {
          throw new Error(`HTTP 响应错误！状态码：${res.status}`);
        }

        return res.json();
      })
      .then((resObj) => {
        setPublicPath(resObj.data + "\\Public");
      })
      .catch((err) => {
        console.error("获取软件公共目录失败", err);
      });
  }, []);

  const validateHtml = (value: string): boolean => {
    return /\.html?(?:\?.*)?$/i.test(value);
  };

  const isUrlInvalid = useMemo(() => {
    if (url === "") return false;

    return !validateHtml(url);
  }, [url]);

  return (
    <div className="flex justify-center">
      <div className="flex flex-col w-full max-w-[800px] py-6 px-10 gap-6">
        <h1 className="text-3xl text-white font-bold leading-9">{t("extension.deployment.title")}</h1>

        <div className="flex items-center justify-center w-full">
          <Alert
            className="font-poppins"
            description={t("extension.deployment.banner")}
            endContent={
              <Button
                size="sm"
                variant="flat"
                onPress={onDrawerOpen}
              >
                {t("camera.viewTutorial")}
              </Button>
            }
            variant="faded"
          />
        </div>

        {/* 操作 */}
        <div className="flex flex-col gap-4 font-poppins">
          <h1 className="text-xl text-default-800 font-bold leading-9">
            {t("lyric.operations")}
          </h1>

          {/* 软件公共目录 */}
          <div className="group relative inline-flex flex-row w-full max-w-full items-center justify-between gap-2 h-16">
            <div className="flex flex-col gap-[2px]">
              <span>{t("extension.deployment.publicPath")}</span>
              <span className="text-color-desc text-sm min-h-[1.25rem]">
                {publicPath || ""}
              </span>
            </div>
            <Button
              variant="ghost"
              onPress={async () => {
                try {
                  await fetch("/api/system/openPublicDir");
                } catch (err) {
                  console.error("打开软件公共目录失败", err);
                }
              }}
            >
              {t("common.open")}
            </Button>
          </div>

          {/* 示例页面 */}
          <div className="group relative inline-flex flex-row w-full max-w-full items-center justify-between gap-2 h-16">
            <div className="flex flex-col gap-[2px]">
              <span>Public Demo</span>
              <span className="text-color-desc text-sm">
                http://localhost:9863/public/example/index.html
              </span>
            </div>
            <Button
              variant="ghost"
              onPress={() => {openExternalUrl("http://localhost:9863/public/example/index.html");}}
            >
              {t("about.view")}
            </Button>
          </div>

          {/* 还原示例页面 */}
          <div className="group relative inline-flex flex-row w-full max-w-full items-center justify-between gap-2 h-16">
            <div className="flex flex-col gap-[2px]">
              <span>{t("extension.deployment.restoreExample")}</span>
              <span className="text-color-desc text-sm">
                Public/example/index.html
              </span>
            </div>
            <Button
              variant="ghost"
              onPress={onRestoreExampleModalOpen}
            >
              {t("common.reset")}
            </Button>
          </div>
        </div>

        <Divider />

        {/* 预览 */}
        <div className="flex flex-col gap-4 font-poppins">
          <h1 className="text-xl text-default-800 font-bold leading-9">
            {t("common.preview")}
          </h1>

          <div className="flex gap-2">
            <Input
              classNames={{
                inputWrapper: "pl-4 pr-0 font-jetbrains",
              }}
              color={isUrlInvalid ? "danger" : "default"}
              errorMessage={t("extension.deployment.invalidUrl")}
              isInvalid={isUrlInvalid}
              endContent={
                <CopyButton copyContent={url} variant="light" />
              }
              type="text"
              variant="bordered"
              value={url}
              onValueChange={setUrl}
            />

            <Button
              className="px-5"
              color={isUrlInvalid ? "default" : "primary"}
              startContent={<Play size={22} />}
              isDisabled={isUrlInvalid}
              onMouseEnter={() => setIsSendHovered(true)}
              onMouseLeave={() => setIsSendHovered(false)}
              onPress={() => {
                setIframeSrc(url);
                setIframeKey((prev) => prev + 1);
              }}
            >
              {t("common.preview")}
            </Button>
          </div>

          {/* 模拟浏览器窗口 */}
          <SimulatedBrowserWindow
            iframeKey={iframeKey}
            iframeSrc={iframeSrc}
            onOpenExternal={() => {openExternalUrl(iframeSrc);}}
            onReload={() => setIframeKey((prev) => prev + 1)}
            autoScrollToBottom={true}
            onResizing={() => {
              scrollToBottom?.("auto");
            }}
          />
        </div>

        <Spacer y={2} />

        {/* 使用说明 */}
        <Drawer
          isOpen={isDrawerOpen}
          size="lg"
          onOpenChange={onDrawerOpenChange}
        >
          <DrawerContent>
            {(onClose) => (
              <>
                <DrawerHeader className="flex flex-col gap-1">
                  <h1 className="text-2xl text-default-800 font-bold leading-12">
                    {t("platformHelp.title")}
                  </h1>
                </DrawerHeader>
                <DrawerBody>
                  <ul className="list-disc flex flex-col gap-2 ml-4">
                    <li className="ps-1 leading-8">
                      {t("common.open")}{" "}
                      <span
                        className="custom-underline font-bold"
                        onClick={async () => {
                          try {
                            await fetch("/api/system/openInstallPath");
                          } catch (err) {
                            console.error("打开软件安装目录失败", err);
                          }
                        }}
                      >
                        {t("about.installPath")}
                      </span>{" "}
                      / <Code className="font-jetbrains">Public</Code>;
                    </li>
                    <li className="ps-1 leading-8">
                      {t("extension.deployment.publicPathTip")}
                    </li>
                    <li className="ps-1 leading-8 font-poppins">
                      URL: <Code className="font-jetbrains">http://localhost:9863/public/.../index.html</Code>
                    </li>
                  </ul>
                </DrawerBody>
                <DrawerFooter>
                  <Button color="default" variant="flat" onPress={onClose}>
                    {t("common.close")}
                  </Button>
                </DrawerFooter>
              </>
            )}
          </DrawerContent>
        </Drawer>

        {/* 还原示例页面模态框 */}
        <Modal
          isOpen={isRestoreExampleModalOpen}
          onOpenChange={onRestoreExampleModalOpenChange}
        >
          <ModalContent>
            {(onClose) => (
              <>
                <ModalHeader className="flex flex-col gap-1">{t("common.notice")}</ModalHeader>
                <ModalBody>
                  <p className="leading-7">
                    {t("extension.deployment.restoreExample")}?
                  </p>
                </ModalBody>
                <ModalFooter>
                  <Button
                    color="default"
                    variant="flat"
                    onPress={onClose}
                  >
                    {t("common.cancel")}
                  </Button>
                  <Button
                    color="primary"
                    onPress={async () => {
                      onClose();

                      try {
                        await fetch("/api/system/restorePublicExample");

                        addToast({
                          color: "success",
                          title: t("common.success"),
                          description: t("extension.deployment.restoreSuccess"),
                          timeout: 3000,
                        });
                      } catch (err) {
                        console.error("还原示例页面失败", err);
                      }
                    }}
                  >
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
