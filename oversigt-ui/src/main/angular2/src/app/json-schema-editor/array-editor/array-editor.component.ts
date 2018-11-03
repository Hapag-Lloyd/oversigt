import { Component, OnInit, Input } from '@angular/core';

@Component({
  selector: 'app-json-array',
  templateUrl: './array-editor.component.html',
  styleUrls: ['./array-editor.component.css']
})
export class ArrayEditorComponent implements OnInit {
  @Input() schemaObject: {};

  constructor() { }

  ngOnInit() {
  }

}
