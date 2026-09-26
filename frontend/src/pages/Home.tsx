import { Layout } from "antd";
import { useEffect } from "react";
import ControlPanel from "@/components/ControlPanel";
import MapContainer from "@/components/MapContainer";
import { useTravelStore } from "@/stores/useTravelStore";

const { Sider, Content } = Layout;

export default function Home() {
  const loadNodes = useTravelStore((s) => s.loadNodes);

  useEffect(() => {
    void loadNodes();
  }, [loadNodes]);

  return (
    <Layout className="h-screen w-screen bg-slate-50">
      <Sider width={360} theme="light" className="border-r border-slate-200">
        <ControlPanel />
      </Sider>
      <Content className="h-screen">
        <MapContainer />
      </Content>
    </Layout>
  );
}
