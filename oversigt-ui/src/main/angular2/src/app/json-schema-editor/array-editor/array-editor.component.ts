import { Component, OnInit, Input } from '@angular/core';
import { AbstractValueAccessor, MakeProvider } from 'src/app/_editor/abstract-value-accessor';
import { JsonSchemaProperty } from '../schema-editor/schema-editor.component';
import { interpret } from 'src/app/utils/interpreter';

@Component({
  selector: 'app-json-array',
  templateUrl: './array-editor.component.html',
  styleUrls: ['./array-editor.component.css'],
  providers: [MakeProvider(ArrayEditorComponent)]
})
export class ArrayEditorComponent extends AbstractValueAccessor implements OnInit {
  @Input() schemaObject: JsonSchemaProperty;
  @Input() displayFormat: string;

  get array(): any[] {
    return this.value;
  }

  ngOnInit() {
    if (this.displayFormat === undefined || this.displayFormat === null || this.displayFormat === '') {
      this.displayFormat = 'grid';
    }
  }

  getNames(): string[] {
    return Object.keys(this.schemaObject.properties);
  }

  addArrayItem() {
    this.value.push(this.createObjectFromProperty(this.schemaObject));
  }

  moveItemUp(item: any) {
    alert('Not implemented yet');
  }

  moveItemDown(item: any) {
    alert('Not implemented yet');
  }

  removeArrayItem(itemToRemove: any) {
    this.value = this.value.filter(item => item !== itemToRemove);
  }

  getTabName(item: any, index: number): string {
    return interpret(item, this.schemaObject.headerTemplate, String(index));
  }

  private createObjectFromProperty(property: JsonSchemaProperty) {
    switch (property.type) {
      case 'string':
        if (property.default !== undefined) {
          return property.default;
        } else if (property.enumSource === undefined) {
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

}
