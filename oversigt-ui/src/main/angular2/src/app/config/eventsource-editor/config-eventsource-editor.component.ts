import { Component, Input, OnInit } from '@angular/core';
import { ControlValueAccessor } from '@angular/forms';
import { EventSourceProperty, SerializableValueService } from 'src/oversigt-client';
import { MakeProvider } from '../../_editor/abstract-value-accessor';

@Component({
  selector: 'app-config-eventsource-editor',
  templateUrl: './config-eventsource-editor.component.html',
  styleUrls: ['./config-eventsource-editor.component.css'],
  providers: [MakeProvider(ConfigEventsourceEditorComponent)]
})
export class ConfigEventsourceEditorComponent implements OnInit, ControlValueAccessor {
  @Input() property: EventSourceProperty;
  @Input() canBeDisabled = false;
  private _value: any = null;
  get value(): any { return this._value; }
  set value(value: any) {
    if (value !== this._value) {
      this._value = value;
      this.onChange(value);
    }
  }

  writeValue(value: any) {
    if (value !== this._value) {
      this._value = value;
      this.onChange(value);
    }
  }

  onChange = (_) => {};
  onTouched = () => {};
  registerOnChange(fn: (_: any) => void): void { this.onChange = fn; }
  registerOnTouched(fn: () => void): void { this.onTouched = fn; }

  constructor(
    private svs: SerializableValueService,
  ) { }

  ngOnInit() {
    const _this = this;
    if (this.property.inputType === 'text' && this.property.allowedValues.length > 0) {
      this.property.inputType = 'enum';
    } else if (this.property.inputType.startsWith('value_')) {
      const type = this.property.inputType.substring('value_'.length);
      this.property.inputType = 'enum';
      this.svs.listProperties(type).subscribe(
        list => {
          // allow empty value
          _this.property.allowedValues.push('0');
          _this.property.allowedValuesMap = {};
          _this.property.allowedValuesMap['0'] = 'NONE';
          // allow other values
          list.forEach(i => {
            _this.property.allowedValues.push(i['id']);
            _this.property.allowedValuesMap[i['id']] = i['name'];
          });
        },
        error => {
          console.error(error);
          alert(error);
        }
      );
    }
  }
}
