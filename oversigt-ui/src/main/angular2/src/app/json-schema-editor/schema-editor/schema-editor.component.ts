import { Component, OnInit, Input } from '@angular/core';
import { MakeProvider, AbstractValueAccessor } from 'src/app/_editor/abstract-value-accessor';

export class JsonSchemaProperty {
  title: string;
  type: string;
  description?: string;
  minLength?: number;
  default?: any;
  minimum?: number;
  maximum?: number;
  properties?: {[key: string]: JsonSchemaProperty};
  format?: string;
  uniqueItems?: boolean;
  items?: JsonSchemaProperty;
  enumSource?: EnumSource[];
  headerTemplate?: string;
}

export class EnumSource {
  source: any[];
  title: string;
  value: string;
}

@Component({
  selector: 'app-json-schema',
  templateUrl: './schema-editor.component.html',
  styleUrls: ['./schema-editor.component.css'],
  providers: [MakeProvider(SchemaEditorComponent)]
})
export class SchemaEditorComponent extends AbstractValueAccessor implements OnInit {
  @Input() schema: '';
  @Input() schemaObject: JsonSchemaProperty = null;
  @Input() showTitles = true;

  ngOnInit() {
    if (this.schemaObject === null) {
      this.schemaObject = JSON.parse(this.schema);
    }
    if (this.value === null) {
      this.value = createObjectFromProperty(this.schemaObject);
    }
  }
}

export function createObjectFromProperty(property: JsonSchemaProperty) {
  switch (property.type) {
    case 'string':
      if (property.default !== undefined) {
        return property.default;
      } else if (property.enumSource === undefined) {
        return '';
      } else {
        // TODO respect values that are already in the array
        return property.enumSource[0].source[0].value;
      }
    case 'number':
      return 0;
    case 'array':
      return [];
    case 'object':
      const obj = {};
      Object.keys(property.properties).forEach(p => {
        obj[p] = this.createObjectFromProperty(property.properties[p]);
      });
      return obj;
  }
  console.error('Unknown type: ', property.type);
}
