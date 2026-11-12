import type { CSSProperties, SVGAttributes } from "react";
import type { IllustrationSpec, Shape } from "./spec";

export interface IllustrationSvgProps extends Omit<SVGAttributes<SVGSVGElement>, "children"> {
  spec: IllustrationSpec;
  /** Play the entrance and ambient motion. Off by default; reduced-motion users never see it. */
  animated?: boolean;
  /** Accessible name. Without it the illustration is decorative and hidden from assistive tech. */
  title?: string;
}

function shapeStyle(shape: Shape): CSSProperties | undefined {
  const style: Record<string, string | number> = {};
  if (shape.delay !== undefined) style["--d"] = shape.delay;
  if (shape.origin) {
    style["--ox"] = `${shape.origin[0]}px`;
    style["--oy"] = `${shape.origin[1]}px`;
  }
  if (shape.blur) style.filter = `blur(${shape.blur}px)`;
  return Object.keys(style).length ? (style as CSSProperties) : undefined;
}

function ShapeNode({ shape }: { shape: Shape }) {
  const common = { "data-f": shape.fill, "data-m": shape.motion, opacity: shape.opacity, style: shapeStyle(shape) };
  switch (shape.kind) {
    case "rect":
      return <rect x={shape.x} y={shape.y} width={shape.w} height={shape.h} rx={shape.r} {...common} />;
    case "circle":
      return <circle cx={shape.cx} cy={shape.cy} r={shape.r} {...common} />;
    case "ellipse":
      return (
        <ellipse
          cx={shape.cx}
          cy={shape.cy}
          rx={shape.rx}
          ry={shape.ry}
          transform={shape.rotate ? `rotate(${shape.rotate} ${shape.cx} ${shape.cy})` : undefined}
          {...common}
        />
      );
    case "path":
      return <path d={shape.d} {...common} />;
  }
}

/**
 * The svg of one illustration, with no hooks and no stylesheet import, so it renders on the server, in a test and in the
 * export script. `Illustration` adds the active theme's art and the stylesheet.
 */
export function IllustrationSvg({ spec, animated = false, title, ...rest }: IllustrationSvgProps) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox={`0 0 ${spec.width} ${spec.height}`}
      width={spec.width}
      height={spec.height}
      role={title ? "img" : undefined}
      aria-hidden={title ? undefined : true}
      data-animated={animated ? "true" : undefined}
      data-illustration={spec.key}
      {...rest}
    >
      {title ? <title>{title}</title> : null}
      {spec.shapes.map((shape, index) =>
        shape.enter === undefined ? (
          <ShapeNode key={index} shape={shape} />
        ) : (
          <g key={index} data-enter="" style={{ "--i": shape.enter } as CSSProperties}>
            <ShapeNode shape={shape} />
          </g>
        ),
      )}
    </svg>
  );
}
