import { Component, OnInit, Input } from '@angular/core';
import { AbstractValueAccessor, MakeProvider } from 'src/app/_editor/abstract-value-accessor';
import { JsonSchemaProperty, createObjectFromProperty } from '../schema-editor/schema-editor.component';
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
  @Input() showMainTitle = false;

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
    this.value.push(createObjectFromProperty(this.schemaObject));
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
}
