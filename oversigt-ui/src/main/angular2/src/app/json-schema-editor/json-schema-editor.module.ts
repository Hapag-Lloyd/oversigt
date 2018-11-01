import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ArrayEditorComponent } from './array-editor/array-editor.component';
import { SchemaEditorComponent } from './schema-editor/schema-editor.component';
import { ObjectEditorComponent } from './object-editor/object-editor.component';
import { StringEditorComponent } from './string-editor/string-editor.component';

@NgModule({
  imports: [
    CommonModule,
  ],
  declarations: [
    ArrayEditorComponent,
    SchemaEditorComponent,
    ObjectEditorComponent,
    StringEditorComponent,
  ],
  exports: [
    SchemaEditorComponent,
  ]
})
export class JsonSchemaEditorModule { }
