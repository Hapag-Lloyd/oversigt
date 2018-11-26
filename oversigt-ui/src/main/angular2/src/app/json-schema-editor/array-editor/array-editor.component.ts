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

  get hasComplexSubtype(): boolean {
    return this.schemaObject.properties !== undefined;
  }

  getNames(): string[] {
    if (this.hasComplexSubtype) {
      return Object.keys(this.schemaObject.properties);
    } else {
      return ['row'];
    }
  }

  getTitle(column: number): string {
    if (this.hasComplexSubtype) {
      return this.schemaObject.properties[this.getNames()[column]].title;
    } else {
      return 'row';
    }
  }

  getValue(column: number, row: number): any {
    if (this.hasComplexSubtype) {
      return this.value[row][this.schemaObject.properties[this.getNames()[column]].title];
    } else {
      return this.value[row];
    }
  }

  getSchema(column: number): JsonSchemaProperty {
    if (this.hasComplexSubtype) {
      return this.schemaObject.properties[column];
    } else {
      return this.schemaObject;
    }
  }

  addArrayItem() {
    this.value.push(this.createObjectFromProperty(this.schemaObject));
  }

  moveItemUp(item: number) {
    alert('Not implemented yet');
  }

  moveItemDown(item: number) {
    alert('Not implemented yet');
  }

  removeArrayItem(itemToRemove: number) {
    this.value.splice(itemToRemove, 1);
  }

  getTabName(item: any, index: number): string {
    return interpret(item, this.schemaObject.headerTemplate, index, 'Item ' + (index + 1));
  }

  private createObjectFromProperty(property: JsonSchemaProperty) {
    switch (property.type) {
      case 'string':
        if (property.default !== undefined) {
          return property.default;
        } else if (property.enumSource === undefined) {
          return '';
        } else {
          // TODO respect values that are already in the array
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
