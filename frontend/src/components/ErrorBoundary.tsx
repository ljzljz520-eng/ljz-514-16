import React from "react";
import { Button, Result } from "antd";

type Props = {
  children: React.ReactNode;
};

type State = {
  hasError: boolean;
};

export class ErrorBoundary extends React.Component<Props, State> {
  state: State = { hasError: false };

  static getDerivedStateFromError() {
    return { hasError: true };
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="min-h-screen flex items-center justify-center bg-slate-50 p-6">
          <Result
            status="error"
            title="页面出现异常"
            subTitle="请刷新页面重试"
            extra={[
              <Button key="reload" type="primary" onClick={() => window.location.reload()}>
                刷新
              </Button>,
            ]}
          />
        </div>
      );
    }

    return this.props.children;
  }
}

