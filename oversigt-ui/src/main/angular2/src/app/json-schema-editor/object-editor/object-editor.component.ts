import { Component, OnInit, Input } from '@angular/core';
import { JsonSchemaProperty } from '../schema-editor/schema-editor.component';

@Component({
  selector: 'app-json-object',
  templateUrl: './object-editor.component.html',
  styleUrls: ['./object-editor.component.css']
})
export class ObjectEditorComponent implements OnInit {
  @Input() schemaObject: JsonSchemaProperty;

  constructor() { }

  ngOnInit() {
  }

  getProperties(): JsonSchemaProperty[] {
    return Object.values(this.schemaObject.properties);
  }
}
