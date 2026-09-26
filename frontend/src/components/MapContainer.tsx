import "leaflet/dist/leaflet.css";
import L from "leaflet";
import { useEffect, useMemo } from "react";
import { MapContainer as LeafletMap, Marker, Polyline, Popup, TileLayer, useMap } from "react-leaflet";
import marker2x from "leaflet/dist/images/marker-icon-2x.png";
import marker from "leaflet/dist/images/marker-icon.png";
import shadow from "leaflet/dist/images/marker-shadow.png";
import { Button } from "antd";
import { useTravelStore } from "@/stores/useTravelStore";

L.Icon.Default.mergeOptions({
  iconRetinaUrl: marker2x,
  iconUrl: marker,
  shadowUrl: shadow,
});

function FitBounds({ points }: { points: Array<[number, number]> }) {
  const map = useMap();
  useEffect(() => {
    if (points.length < 2) return;
    const bounds = L.latLngBounds(points.map((p) => L.latLng(p[0], p[1])));
    map.fitBounds(bounds.pad(0.15));
  }, [map, points]);
  return null;
}

export default function MapContainer() {
  const nodes = useTravelStore((s) => s.nodes);
  const startId = useTravelStore((s) => s.startId);
  const endId = useTravelStore((s) => s.endId);
  const setStartId = useTravelStore((s) => s.setStartId);
  const setEndId = useTravelStore((s) => s.setEndId);
  const route = useTravelStore((s) => s.route);

  const center: [number, number] = [29.56301, 106.57577];
  const routePoints = useMemo(() => {
    if (!route) return [] as Array<[number, number]>;
    return route.pathNodes.map((n) => [n.lat, n.lng] as [number, number]);
  }, [route]);

  return (
    <LeafletMap center={center} zoom={12} className="h-full w-full">
      <TileLayer
        attribution="&copy; OpenStreetMap contributors"
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
      />

      {nodes.map((n) => {
        const isStart = n.id === startId;
        const isEnd = n.id === endId;
        let icon: L.Icon | L.DivIcon | undefined;
        if (isStart) {
          icon = L.divIcon({
            className: "",
            html:
              "<div style='width:18px;height:18px;border-radius:999px;background:#16a34a;border:2px solid white;box-shadow:0 6px 18px rgba(0,0,0,.18)'></div>",
            iconSize: [18, 18],
            iconAnchor: [9, 9],
          });
        } else if (isEnd) {
          icon = L.divIcon({
            className: "",
            html:
              "<div style='width:18px;height:18px;border-radius:999px;background:#dc2626;border:2px solid white;box-shadow:0 6px 18px rgba(0,0,0,.18)'></div>",
            iconSize: [18, 18],
            iconAnchor: [9, 9],
          });
        }
        return (
          <Marker key={n.id} position={[n.lat, n.lng]} {...(icon ? { icon } : {})}>
            <Popup>
              <div className="min-w-[220px]">
                <div className="text-sm font-semibold text-slate-900">{n.name}</div>
                {n.desc ? <div className="mt-1 text-xs text-slate-600">{n.desc}</div> : null}
                <div className="mt-3 grid grid-cols-2 gap-2">
                  <Button size="small" onClick={() => setStartId(n.id)}>
                    设为起点
                  </Button>
                  <Button size="small" type="primary" onClick={() => setEndId(n.id)}>
                    设为终点
                  </Button>
                </div>
              </div>
            </Popup>
          </Marker>
        );
      })}

      {routePoints.length >= 2 ? <Polyline positions={routePoints} pathOptions={{ color: "#2563eb", weight: 5, opacity: 0.9 }} /> : null}
      {routePoints.length >= 2 ? <FitBounds points={routePoints} /> : null}
    </LeafletMap>
  );
}
