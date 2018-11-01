import { Component, OnInit, Input } from '@angular/core';
import { JsonSchemaProperty } from '../schema-editor/schema-editor.component';

@Component({
  selector: 'app-json-string',
  templateUrl: './string-editor.component.html',
  styleUrls: ['./string-editor.component.css']
})
export class StringEditorComponent implements OnInit {
  @Input() schemaObject: JsonSchemaProperty;

  constructor() { }

  ngOnInit() {
  }

}
