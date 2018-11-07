import { Component, OnInit, Input } from '@angular/core';
import { JsonSchemaProperty } from '../schema-editor/schema-editor.component';
import { AbstractValueAccessor, MakeProvider } from 'src/app/_editor/abstract-value-accessor';

@Component({
  selector: 'app-json-object',
  templateUrl: './object-editor.component.html',
  styleUrls: ['./object-editor.component.css'],
  providers: [MakeProvider(ObjectEditorComponent)]
})
export class ObjectEditorComponent extends AbstractValueAccessor implements OnInit {
  @Input() schemaObject: JsonSchemaProperty;
  @Input() showTitles = true;

  ngOnInit() {
  }

  getPropertyNames(): string[] {
    return Object.keys(this.schemaObject.properties);
  }

  getProperty(name: string): JsonSchemaProperty {
    return this.schemaObject.properties[name];
  }
}
