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

export function createObjectFromProperty(property: JsonSchemaProperty) {
  switch (property.type) {
    case 'string':
      if (property.default !== undefined) {
        return property.default;
      } else if (property.enumSource === undefined) {
        return '';
      } else {
        // TODO: respect values that are already in the array
        return property.enumSource[0].source[0].value;
      }
    case 'number':
      return 0;
    case 'array':
      return [];
    case 'object':
      const obj = {};
      Object.keys(property.properties).forEach(p => {
        obj[p] = createObjectFromProperty(property.properties[p]);
      });
      return obj;
    case 'boolean':
      return false;
  }
  console.error('Unknown type: ', property.type);
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
  @Input() showMainTitle = false;

  ngOnInit() {
    if (this.schemaObject === null) {
      this.schemaObject = JSON.parse(this.schema);
    }
  }

  writeValue(value: any) {
    if (value !== null) {
      super.writeValue(value);
    } else {
      super.writeValue(createObjectFromProperty(this.schemaObject));
    }
    // warning: comment below if only want to emit on user intervention
    // this.onChange(value);
  }
}
