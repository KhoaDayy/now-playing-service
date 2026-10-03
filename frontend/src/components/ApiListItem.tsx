import { Radio } from "@heroui/radio";
import { Chip } from "@heroui/chip";
import { InfoCircle } from "@mynaui/icons-react";
import { Tooltip } from "@heroui/tooltip";
import { useTranslation } from "react-i18next";

const cn = (...classNames: (string | undefined | null | false)[]) => {
  return classNames.filter(Boolean).join(" ");
};

const methodConfig = {
  GET: {
    color: "success" as const,
    baseClass: "bg-[#192c2c]",
  },
  POST: {
    color: "warning" as const,
    baseClass: "bg-[#332323]",
  },
  WS: {
    color: "secondary" as const,
    baseClass: "bg-[#26223d]",
  },
};

const endpointKeyMap: Record<string, { desc: string; info?: string }> = {
  "/api/query": { desc: "query" },
  "/api/query/track": { desc: "queryTrack" },
  "/api/query/hasSong": { desc: "queryHasSong" },
  "/api/query/player": { desc: "queryPlayer" },
  "/api/query/progress": { desc: "queryProgress" },
  "/api/lyric": { desc: "lyric", info: "lyricInfo" },
  "/api/audio/devices": { desc: "audioDevices" },
  "/api/query/isConnected": { desc: "isConnected" },
  "/api/version": { desc: "version" },
  "/api/system/appInfo": { desc: "appInfo" },
  "/api/system/log/mainContent": { desc: "logMain" },
  "/api/system/networkInterfaces": { desc: "networkInterfaces", info: "networkInterfacesInfo" },
  "/api/system/lanDevices": { desc: "lanDevices" },
  "/api/cover/convert": { desc: "coverConvert", info: "coverConvertInfo" },
  "/api/cover/videoUrl": { desc: "coverVideoUrl" },
  "/api/ws/lyric": { desc: "wsLyric", info: "wsLyricInfo" },
};

export const ApiListItem = (props: any) => {
  const { children, ...otherProps } = props;
  const { t } = useTranslation();

  const currentMethodConfig =
    methodConfig[props.method as keyof typeof methodConfig] ||
    methodConfig.POST;

  const keyConfig = endpointKeyMap[props.path];
  const translatedDesc = keyConfig ? String(t(`api.endpoints.${keyConfig.desc}`, { defaultValue: props.desc })) : props.desc;
  const translatedInfo = keyConfig?.info ? String(t(`api.endpoints.${keyConfig.info}`, { defaultValue: props.info })) : props.info;
  const translatedTag = String(t(`api.tags.${props.tag}`, { defaultValue: props.tag }));

  return (
    <Radio
      {...otherProps}
      classNames={{
        base: cn(
          "inline-flex m-0 bg-content1 hover:bg-content2 items-center justify-between",
          "flex-row max-w-none w-full cursor-pointer rounded-lg gap-4 p-4 border-2 border-transparent",
          "data-[selected=true]:border-primary transition-all duration-150",
        ),
        wrapper: "hidden",
        labelWrapper: "w-full",
      }}
    >
      <div className="flex w-full items-center justify-between">
        <div className="flex items-center gap-4">
          <Chip
            className="px-2"
            classNames={{
              base: currentMethodConfig.baseClass,
            }}
            color={currentMethodConfig.color}
            radius="sm"
            size="sm"
            variant="bordered"
          >
            {props.method}
          </Chip>
          <div className="flex flex-col">
            <span className="font-jetbrains text-sm">{props.path}</span>
            <span className="flex items-center text-sm text-default-500">
              {translatedDesc}
              {translatedInfo && (
                <Tooltip
                  className="px-3"
                  closeDelay={200}
                  color="foreground"
                  content={translatedInfo}
                  delay={200}
                  offset={10}
                  placement="bottom"
                >
                  <InfoCircle className="ml-1 z-[2]" size={16} />
                </Tooltip>
              )}
            </span>
          </div>
        </div>
        <div>
          <Chip className="px-1">{translatedTag}</Chip>
        </div>
      </div>
      {children}
    </Radio>
  );
};
