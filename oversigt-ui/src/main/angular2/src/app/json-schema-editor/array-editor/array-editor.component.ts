import { Component, OnInit, Input } from '@angular/core';
import { AbstractValueAccessor } from 'src/app/_editor/abstract-value-accessor';

@Component({
  selector: 'app-json-array',
  templateUrl: './array-editor.component.html',
  styleUrls: ['./array-editor.component.css']
})
export class ArrayEditorComponent extends AbstractValueAccessor implements OnInit {
  @Input() schemaObject: {};

  get array(): any[] {
    return this.value;
  }

  ngOnInit() {
  }

}
