import { Typography } from "antd";
import type { ManualSection } from "../content/userManualSections";

const { Paragraph, Text, Title } = Typography;

interface ManualSectionViewProps {
  section: ManualSection;
}

export default function ManualSectionView({ section }: ManualSectionViewProps) {
  return (
    <div className="manual-section-view">
      {section.intro ? (
        <Paragraph type="secondary" className="manual-section-intro">
          {section.intro}
        </Paragraph>
      ) : null}
      {section.items.map((item) => (
        <div key={item.title} className="manual-item">
          <Title level={5} className="manual-item-title">
            {item.title}
          </Title>
          {item.paragraphs.map((paragraph) => (
            <Paragraph key={paragraph} className="manual-item-paragraph">
              {paragraph}
            </Paragraph>
          ))}
          {item.bullets && item.bullets.length > 0 ? (
            <ul className="manual-item-list">
              {item.bullets.map((bullet) => (
                <li key={bullet}>
                  <Text>{bullet}</Text>
                </li>
              ))}
            </ul>
          ) : null}
        </div>
      ))}
    </div>
  );
}
