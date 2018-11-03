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

  ngOnInit() {
  }

  getProperties(): JsonSchemaProperty[] {
    return Object.values(this.schemaObject.properties);
  }
}
