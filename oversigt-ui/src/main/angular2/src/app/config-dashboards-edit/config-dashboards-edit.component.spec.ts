import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { ConfigDashboardsEditComponent } from './config-dashboards-edit.component';

describe('ConfigDashboardsEditComponent', () => {
  let component: ConfigDashboardsEditComponent;
  let fixture: ComponentFixture<ConfigDashboardsEditComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ ConfigDashboardsEditComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ConfigDashboardsEditComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
