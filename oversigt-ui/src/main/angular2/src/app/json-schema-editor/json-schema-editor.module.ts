import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ArrayEditorComponent } from './array-editor/array-editor.component';
import { SchemaEditorComponent } from './schema-editor/schema-editor.component';
import { ObjectEditorComponent } from './object-editor/object-editor.component';
import { StringEditorComponent } from './string-editor/string-editor.component';
import { FormsModule } from '@angular/forms';
import { NgZorroAntdModule, NZ_I18N, en_US } from 'ng-zorro-antd';
import { ClarityModule } from '@clr/angular';

@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    NgZorroAntdModule,
    ClarityModule,
  ],
  declarations: [
    ArrayEditorComponent,
    SchemaEditorComponent,
    ObjectEditorComponent,
    StringEditorComponent,
  ],
  exports: [
    SchemaEditorComponent,
  ],
  providers: [ { provide: NZ_I18N, useValue: en_US } ]
})
export class JsonSchemaEditorModule { }
