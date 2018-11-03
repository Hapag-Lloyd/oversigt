import {Component, Input} from '@angular/core';
import { MakeProvider, AbstractValueAccessor } from './abstract-value-accessor';

@Component({
  selector : 'app-inputfield',
  templateUrl: './genericinput.component.ng2.html',
  providers: [MakeProvider(InputFieldComponent)]
})
export class InputFieldComponent extends AbstractValueAccessor {
  @Input('displaytext') displaytext: string;
  @Input('placeholder') placeholder: string;
}
