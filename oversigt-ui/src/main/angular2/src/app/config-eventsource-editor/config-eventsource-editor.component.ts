import { Component, Provider, forwardRef, Input, OnInit } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { EventSourceProperty, SerializableValueService } from 'src/oversigt-client';

export const CUSTOM_INPUT_CONTROL_VALUE_ACCESSOR: any = [{
  provide: NG_VALUE_ACCESSOR,
  useExisting: forwardRef(() => ConfigEventsourceEditorComponent),
  multi: true
}];

@Component({
  selector: 'app-config-eventsource-editor',
  templateUrl: './config-eventsource-editor.component.html',
  styleUrls: ['./config-eventsource-editor.component.css'],
  providers: CUSTOM_INPUT_CONTROL_VALUE_ACCESSOR,
})
export class ConfigEventsourceEditorComponent implements OnInit, ControlValueAccessor {
  @Input() property: EventSourceProperty;
  @Input() canBeDisabled = false;
  private _value: any = '';
  get value(): any { return this._value; }
  set value(v: any) {
    if (v !== this._value) {
      this._value = v;
      this.onChange(v);
    }
  }

  writeValue(value: any) {
    this._value = value;
    this.onChange(value);
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
