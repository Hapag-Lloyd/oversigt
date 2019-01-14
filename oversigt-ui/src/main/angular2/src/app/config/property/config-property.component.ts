import { Component, OnInit, Input, OnDestroy, TemplateRef, ViewChild, ElementRef } from '@angular/core';
import { SerializableValueService, SerializablePropertyMember } from 'src/oversigt-client';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { NotificationService } from 'src/app/services/notification.service';
import { ClrLoadingState } from '@clr/angular';

export enum ModalVerb {
  Create = 'Create', Edit = 'Edit'
}

@Component({
  selector: 'app-config-property',
  templateUrl: './config-property.component.html',
  styleUrls: ['./config-property.component.css']
})
export class ConfigPropertyComponent implements OnInit, OnDestroy {
  @ViewChild('cancelButton') cancelButton: ElementRef;
  @ViewChild('okButton') okButton: ElementRef;
  private subscription: Subscription = null;
  propertyType: string;
  members: SerializablePropertyMember[] = [];
  values: object[] = [];
  valueToCreate = {};

  modalVerb: ModalVerb = ModalVerb.Create;
  modalShowing = false;
  modalLoadingState = ClrLoadingState.DEFAULT;

  constructor(
    private route: ActivatedRoute,
    private notification: NotificationService,
    private svs: SerializableValueService,
  ) { }

  ngOnInit() {
    this.propertyType = this.route.snapshot.paramMap.get('name');
    this.subscription = this.route.url.subscribe(_ => {
      const newType = this.route.snapshot.paramMap.get('name');
      if (newType !== this.propertyType) {
        this.propertyType = newType;
        this.initComponent();
      }
    });
    this.initComponent();
  }

  ngOnDestroy() {
    if (this.subscription !== null) {
      this.subscription.unsubscribe();
    }
  }

  private setValues(values: object[]): void {
    this.values = values.sort((a, b) => a['name'].toLowerCase() > b['name'].toLowerCase() ? 1 : -1);
  }

  private initComponent() {
    this.valueToCreate = {};
    this.members = [];
    this.svs.readMembers(this.propertyType).subscribe(
      members => {
        this.members = members;
        this.svs.listProperties(this.propertyType).subscribe(
          values => this.setValues(values)
        );
      },
      error => {
        console.error('Cannot load property type "' + this.propertyType + '"' + error);
        this.propertyType = null;
        this.members = [];
      }
    );
  }

  deleteValue(id: number): void {
    const valueToDelete = this.getValue(id);
    this.svs.deleteProperty(this.propertyType, id).subscribe(
      ok => {
        this.setValues(this.values.filter(e => e['id'] !== id));
        this.notification.success('Property "' + valueToDelete['name'] + '" has been deleted.');
      },
      error => alert('There was an error while deleting the property: ' + error)
    );
  }

  private getValue(id: number) {
    return this.values.find(v => v['id'] === id);
  }

  showCreateModal(): void {
    this.showModal(ModalVerb.Create, {});
  }

  showEditModal(id: number): void {
    this.showModal(ModalVerb.Edit, this.createCopyOf(id));
  }

  private createCopyOf(id: number): {} {
    const value = {};
    const valueToEdit = this.getValue(id);
    value['id'] = valueToEdit['id'];
    this.members.forEach(m => {
      value[m.name] = valueToEdit[m.name];
    });
    return value;
  }

  private showModal(verb: ModalVerb, value: {}): void {
    this.valueToCreate = value;
    this.modalVerb = verb;
    this.modalLoadingState = ClrLoadingState.DEFAULT;
    this.modalShowing = true;
  }

  clickCancelModal(): void {
    this.modalShowing = false;
  }

  clickOkInCreateModal(): void {
    this.modalLoadingState = ClrLoadingState.LOADING;
    this.svs.createProperty(this.propertyType, this.valueToCreate).subscribe(
      createdValue => {
        this.values.push(createdValue);
        this.setValues(this.values); // sort the stuff...
        this.notification.success('Property "' + createdValue['name'] + '" has been created.');
        this.modalLoadingState = ClrLoadingState.SUCCESS;
        this.modalShowing = false;
      },
      error => {
        this.modalLoadingState = ClrLoadingState.ERROR;
        if (error.error && error.error.errors && error.error.errors instanceof Array) {
          error.error.errors.forEach(message => {
            this.notification.error(message);
          });
        } else {
          // TODO: error handling
          console.log(error);
          alert(error);
        }
      }
    );
  }

  clickOkInEditModal(): void {
    this.modalLoadingState = ClrLoadingState.LOADING;
    const id = this.valueToCreate['id'];
    delete this.valueToCreate['id'];
    this.svs.updateProperty(this.propertyType, id, this.valueToCreate).subscribe(
      editedValue => {
        const value = this.values.find(v => v['id'] === editedValue['id']);
        this.members.forEach(m => {
          value[m.name] = editedValue[m.name];
        });
        this.setValues(this.values); // sort the stuff...
        this.notification.success('Property "' + editedValue['name'] + '" has been updated.');
        this.modalLoadingState = ClrLoadingState.SUCCESS;
        this.modalShowing = false;
      },
      error => {
        this.modalLoadingState = ClrLoadingState.ERROR;
        if (error.error && error.error.errors && error.error.errors instanceof Array) {
          error.error.errors.forEach(message => {
            this.notification.error(message);
          });
        } else {
          // TODO: error handling
          console.log(error);
          alert(error);
        }
      }
    );
  }
}
