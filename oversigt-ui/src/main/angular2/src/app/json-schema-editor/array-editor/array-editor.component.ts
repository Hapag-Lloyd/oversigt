import { Component, OnInit, Input } from '@angular/core';
import { AbstractValueAccessor, MakeProvider } from 'src/app/_editor/abstract-value-accessor';
import { JsonSchemaProperty } from '../schema-editor/schema-editor.component';

@Component({
  selector: 'app-json-array',
  templateUrl: './array-editor.component.html',
  styleUrls: ['./array-editor.component.css'],
  providers: [MakeProvider(ArrayEditorComponent)]
})
export class ArrayEditorComponent extends AbstractValueAccessor implements OnInit {
  @Input() schemaObject: JsonSchemaProperty;

  get array(): any[] {
    return this.value;
  }

  ngOnInit() {
  }

  addArrayItem() {
    this.value.push(this.createObjectFromProperty(this.schemaObject));
  }

  private createObjectFromProperty(property: JsonSchemaProperty) {
    switch (property.type) {
      case 'string':
        if (property.enumSource === undefined) {
          return '';
        } else {
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

  removeArrayItem(index: number) {
    //
  }

}
