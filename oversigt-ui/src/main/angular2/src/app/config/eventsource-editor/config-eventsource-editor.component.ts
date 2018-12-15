import { Component, Input, OnInit, Output, EventEmitter } from '@angular/core';
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
  // the property to handle
  @Input() property: EventSourceProperty;
  @Output() propertyDeletion = new EventEmitter();
  @Input() fixed = false;

  // how to handle disabled values
  @Input() canBeDisabled = false;
  private _enabled = false;
  get enabled(): boolean {
    return this._enabled;
  }
  set enabled(enabled: boolean) {
    this.setValue(this.value, enabled);
  }

  // accessors for ngModel
  private _value: any = null;
  private setValue(value: any, enabled: boolean): void {
    const oldValue = this._value;
    if (enabled) {
      this._value = value;
      this._enabled = true;
    } else {
      this._value = null;
      this._enabled = false;
      this.propertyDeletion.emit();
    }
    if (oldValue !== this._value) {
      this.onChange(value);
    }
  }
  get value(): any { return this._value; }
  set value(value: any) {
    this.setValue(value, value !== undefined);
    /*this.enabled = value !== undefined;
    if (value !== this._value) {
      this._value = value;
      this.onChange(value);
    }*/
  }

  writeValue(value: any) {
    this.setValue(value, value !== undefined);
    /*this.enabled = value !== undefined;
    if (value !== this._value) {
      this._value = value;
      this.onChange(value);
    }*/
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

  toString(object: any): string {
    return JSON.stringify(object);
  }

}
