import { Component, OnInit, Input } from '@angular/core';
import { JsonSchemaProperty } from '../schema-editor/schema-editor.component';
import { AbstractValueAccessor, MakeProvider } from 'src/app/_editor/abstract-value-accessor';

@Component({
  selector: 'app-json-string',
  templateUrl: './string-editor.component.html',
  styleUrls: ['./string-editor.component.css'],
  providers: [MakeProvider(StringEditorComponent)]
})
export class StringEditorComponent extends AbstractValueAccessor implements OnInit {
  @Input() schemaObject: JsonSchemaProperty;
  @Input() showTitle: true;
  editorType = 'input';
  inputType = null;
  values: string[];
  valueToTitle: {[key: string]: string} = {};

  ngOnInit() {
    // TODO: handle unique items

    if (this.schemaObject.enumSource !== undefined && this.schemaObject.enumSource.length === 1) {
      this.editorType = 'select';
      const enumSource = this.schemaObject.enumSource[0];
      // TODO: use interpreter code here
      const valueIndex = enumSource.value.replace(/{/g, '').replace(/}/g, '').substring('item.'.length);
      const titleIndex = enumSource.title.replace(/{/g, '').replace(/}/g, '').substring('item.'.length);
      this.values = enumSource.source.map(item => item[valueIndex]);
      const titles = enumSource.source.map(item => item[titleIndex]);
      for (let i = 0; i < this.values.length; ++i) {
        this.valueToTitle[this.values[i]] = titles[i];
      }
    } else if (this.schemaObject.type === 'number') {
      this.editorType = 'number';
    } else if (this.schemaObject.type === 'boolean') {
      this.editorType = 'boolean';
    } else {
      switch (this.schemaObject.format) {
        case 'text':
        case null:
        case '':
        case 'hostname':
          this.inputType = 'text';
          break;
        case 'color':
          this.inputType = 'color';
          break;
        case 'date':
          this.inputType = 'date';
          break;
        case 'password':
          this.inputType = 'password';
          break;
        case 'email':
          this.inputType = 'email';
          break;
        default:
          this.inputType = this.schemaObject.format;
          break;
      }
    }
  }

}
