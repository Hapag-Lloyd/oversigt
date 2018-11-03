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

  ngOnInit() {
    if (this.schemaObject === null) {
      this.schemaObject = JSON.parse(this.schema);
    }
  }
}
